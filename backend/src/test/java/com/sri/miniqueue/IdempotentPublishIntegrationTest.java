package com.sri.miniqueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.dto.PublishResponse;
import com.sri.miniqueue.exception.ConflictException;
import com.sri.miniqueue.exception.ResourceNotFoundException;
import com.sri.miniqueue.service.ClientService;
import com.sri.miniqueue.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdempotentPublishIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MessageService messageService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String suffix;
    private String topic;
    private String queue;

    @BeforeEach
    void createTopicAndQueue() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        topic = "topic-" + suffix;
        queue = "queue-" + suffix;
        messageService.createTopic(topic);
        messageService.createQueue(queue, topic);
    }

    private int storedMessages() {
        return messageService.getMessagesByQueue(queue).size();
    }

    @Test
    void repeatingAKeyReturnsTheOriginalMessagesAndStoresNothingNew() {
        UUID publisher = UUID.randomUUID();

        PublishResponse first = messageService.publishMessage(topic, "order-42", publisher, "order-42");
        PublishResponse retry = messageService.publishMessage(topic, "order-42", publisher, "order-42");

        assertFalse(first.isDuplicate());
        assertTrue(retry.isDuplicate());
        assertEquals(first.getMessageIds(), retry.getMessageIds());
        assertEquals(1, storedMessages());
    }

    @Test
    void reusingAKeyForADifferentPayloadIsAConflict() {
        UUID publisher = UUID.randomUUID();
        messageService.publishMessage(topic, "first", publisher, "k-1");

        assertThrows(ConflictException.class, () -> messageService.publishMessage(topic, "second", publisher, "k-1"));
        assertEquals(1, storedMessages());
    }

    @Test
    void keysBelongToOnePublisher() {
        messageService.publishMessage(topic, "same", UUID.randomUUID(), "shared-key");
        messageService.publishMessage(topic, "same", UUID.randomUUID(), "shared-key");

        assertEquals(2, storedMessages(), "two publishers may use the same key independently");
    }

    @Test
    void withoutAKeyEveryCallStoresANewMessage() {
        messageService.publishMessage(topic, "x");
        messageService.publishMessage(topic, "x");

        assertEquals(2, storedMessages());
    }

    @Test
    void aFailedPublishDoesNotUseUpItsKey() {
        UUID publisher = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class,
                () -> messageService.publishMessage("no-such-topic-" + suffix, "x", publisher, "k-2"));

        // The claim rolled back with the failed publish, so the key still works.
        PublishResponse retry = messageService.publishMessage(topic, "x", publisher, "k-2");
        assertFalse(retry.isDuplicate());
    }

    @Test
    void concurrentRetriesWithOneKeyStoreExactlyOneMessage() throws Exception {
        UUID publisher = UUID.randomUUID();
        int threads = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<PublishResponse>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                startGate.await();
                return messageService.publishMessage(topic, "order-7", publisher, "order-7");
            }));
        }
        startGate.countDown();

        List<PublishResponse> responses = new ArrayList<>();
        for (Future<PublishResponse> result : results) {
            responses.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertEquals(1, responses.stream().filter(r -> !r.isDuplicate()).count(), "exactly one call really publishes");
        assertEquals(1, responses.stream().map(PublishResponse::getMessageIds).distinct().count(), "all calls see the same ids");
        assertEquals(1, storedMessages());
    }

    @Test
    void theHeaderWorksOverHttp() throws Exception {
        String key = clientService.createClient("pub-" + suffix, List.of("publish:" + topic)).getApiKey();

        JsonNode first = publishOverHttp(key, "http-1");
        JsonNode retry = publishOverHttp(key, "http-1");

        assertFalse(first.path("duplicate").asBoolean());
        assertTrue(retry.path("duplicate").asBoolean());
        assertEquals(first.path("message_ids"), retry.path("message_ids"));

        mvc.perform(post("/api/v1/topics/" + topic + "/messages")
                        .header(AUTHORIZATION, "Bearer " + key).header("Idempotency-Key", "has space")
                        .contentType(APPLICATION_JSON).content("{\"payload\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode publishOverHttp(String apiKey, String idempotencyKey) throws Exception {
        String body = mvc.perform(post("/api/v1/topics/" + topic + "/messages")
                        .header(AUTHORIZATION, "Bearer " + apiKey)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"payload\":\"hello\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("response_body");
    }
}