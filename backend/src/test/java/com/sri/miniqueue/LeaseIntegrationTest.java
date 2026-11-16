package com.sri.miniqueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.dto.ConsumeResponse;
import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.exception.ConflictException;
import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.scheduler.RedeliveryScheduler;
import com.sri.miniqueue.service.ClientService;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.to.MessageStatus;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        // Park the background scheduler so it can't race the tests. Each test runs it by hand.
        "miniqueue.redelivery.fixed-delay-ms=3600000"
})
@AutoConfigureMockMvc
@Testcontainers
class LeaseIntegrationTest {

    private static final String INSTANCE = "MiniQueue-Instance";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RedeliveryScheduler scheduler;

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

    // ---------- helpers ----------

    private ConsumeResponse publishAndConsume(String consumerId) {
        messageService.publishMessage(topic, "turn");
        return messageService.consumeMessage(queue, consumerId);
    }

    private Message reload(UUID id) {
        return messageRepository.findById(id).orElseThrow();
    }

    /** Moves the lease into the past, as if the consumer had gone silent. Steadier and faster than sleeping. */
    private void expireLease(UUID id) {
        Message msg = reload(id);
        msg.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
        messageRepository.save(msg);
    }

    // ---------- consume ----------

    @Test
    void consumeGivesALeaseOfTheQueuesLength() {
        messageService.updateQueueLease(queue, 15);

        ConsumeResponse consumed = publishAndConsume("worker");
        Message msg = reload(consumed.getMessageId());

        assertEquals(15, consumed.getLeaseSeconds());
        assertEquals(15, Duration.between(msg.getUnackedAt(), msg.getLeaseExpiresAt()).toSeconds());
    }

    // ---------- redelivery ----------

    @Test
    void anExpiredLeaseIsRedeliveredToTheNextConsumer() {
        messageService.updateQueueLease(queue, 5);
        UUID id = publishAndConsume("worker-1").getMessageId();

        expireLease(id);
        scheduler.redeliverExpiredLeases();

        Message msg = reload(id);
        assertEquals(MessageStatus.PENDING, msg.getStatus());
        assertEquals(1, msg.getRetryCount());
        assertNull(msg.getConsumerId());
        assertNull(msg.getLeaseExpiresAt(), "a PENDING message holds no lease");

        assertEquals(id, messageService.consumeMessage(queue, "worker-2").getMessageId(),
                "another consumer picks it up");
    }

    @Test
    void aLiveLeaseIsNotRedelivered() {
        messageService.updateQueueLease(queue, 5);
        UUID id = publishAndConsume("worker").getMessageId();

        scheduler.redeliverExpiredLeases();

        Message msg = reload(id);
        assertEquals(MessageStatus.UNACKED, msg.getStatus());
        assertEquals("worker", msg.getConsumerId());
    }

    // ---------- heartbeat ----------

    @Test
    void aHeartbeatPushesTheLeaseForward() {
        messageService.updateQueueLease(queue, 5);
        UUID id = publishAndConsume("worker").getMessageId();
        LocalDateTime before = reload(id).getLeaseExpiresAt();

        int leaseSeconds = messageService.heartbeat(id, "worker");

        assertEquals(5, leaseSeconds);
        assertTrue(reload(id).getLeaseExpiresAt().isAfter(before));
    }

    @Test
    void aHeartbeatRescuesAnExpiredLeaseThatWasNotYetReclaimed() {
        messageService.updateQueueLease(queue, 5);
        UUID id = publishAndConsume("worker").getMessageId();

        expireLease(id);
        messageService.heartbeat(id, "worker"); // nobody else holds it yet, so extending is safe
        scheduler.redeliverExpiredLeases();

        Message msg = reload(id);
        assertEquals(MessageStatus.UNACKED, msg.getStatus());
        assertEquals(0, msg.getRetryCount() == null ? 0 : msg.getRetryCount());
        assertTrue(msg.getLeaseExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void aHeartbeatFromAnotherConsumerIsRejected() {
        UUID id = publishAndConsume("worker-1").getMessageId();

        assertThrows(ConflictException.class, () -> messageService.heartbeat(id, "worker-2"));
    }

    @Test
    void aStalledConsumerLosesItsLeaseAfterRedelivery() {
        messageService.updateQueueLease(queue, 5);
        UUID id = publishAndConsume("worker-1").getMessageId();

        expireLease(id);
        scheduler.redeliverExpiredLeases();
        messageService.consumeMessage(queue, "worker-2");

        // Fencing: the old holder is rejected, the new one is accepted
        assertThrows(ConflictException.class, () -> messageService.heartbeat(id, "worker-1"));
        assertThrows(ConflictException.class, () -> messageService.ackMessage(id, "worker-1"));
        assertEquals(5, messageService.heartbeat(id, "worker-2"));
    }

    @Test
    void aHeartbeatAfterAckIsRejected() {
        UUID id = publishAndConsume("worker").getMessageId();
        messageService.ackMessage(id, "worker");

        assertThrows(ConflictException.class, () -> messageService.heartbeat(id, "worker"));
    }

    // ---------- backward compatibility ----------

    @Test
    void aQueueWithoutALeaseKeepsTheTenMinuteDefault() {
        ConsumeResponse consumed = publishAndConsume("worker");
        Message msg = reload(consumed.getMessageId());

        assertEquals(600, consumed.getLeaseSeconds());
        assertEquals(600, Duration.between(msg.getUnackedAt(), msg.getLeaseExpiresAt()).toSeconds());

        scheduler.redeliverExpiredLeases();
        assertEquals(MessageStatus.UNACKED, reload(consumed.getMessageId()).getStatus(),
                "a consumer that never heartbeats is not redelivered early");
    }

    // ---------- queue settings ----------

    @Test
    void leaseLengthMustBeInRangeAndNullResetsToTheDefault() {
        assertThrows(CustomException.class, () -> messageService.updateQueueLease(queue, 4));
        assertThrows(CustomException.class, () -> messageService.updateQueueLease(queue, 43_201));

        messageService.updateQueueLease(queue, 15);
        messageService.updateQueueLease(queue, null);

        assertEquals(600, publishAndConsume("worker").getLeaseSeconds());
    }

    // ---------- replay ----------

    @Test
    void replayClearsTheOldLease() {
        UUID id = publishAndConsume("worker").getMessageId();
        messageService.nackMessage(id, "worker", false); // straight to DEAD

        messageService.replayMessage(id);

        Message msg = reload(id);
        assertEquals(MessageStatus.PENDING, msg.getStatus());
        assertNull(msg.getLeaseExpiresAt());
    }

    // ---------- over HTTP: security rule, instance header, JSON shape ----------

    @Test
    void heartbeatWorksOverHttpAndFencesOtherInstances() throws Exception {
        String key = clientService.createClient("worker-" + suffix, List.of("consume:" + queue)).getApiKey();
        messageService.updateQueueLease(queue, 15);
        messageService.publishMessage(topic, "turn");

        String consumed = mvc.perform(post("/api/v1/queues/" + queue + "/consume")
                        .header(AUTHORIZATION, "Bearer " + key)
                        .header(INSTANCE, "pod-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = json.readTree(consumed).path("response_body");
        assertEquals(15, body.path("lease_seconds").asInt());
        String id = body.path("message_id").asText();

        mvc.perform(post("/api/v1/messages/" + id + "/heartbeat")
                        .header(AUTHORIZATION, "Bearer " + key)
                        .header(INSTANCE, "pod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response_body.lease_seconds").value(15));

        // Same client, different replica: it does not hold this message
        mvc.perform(post("/api/v1/messages/" + id + "/heartbeat")
                        .header(AUTHORIZATION, "Bearer " + key)
                        .header(INSTANCE, "pod-2"))
                .andExpect(status().isConflict());

        // No credentials at all
        mvc.perform(post("/api/v1/messages/" + id + "/heartbeat"))
                .andExpect(status().isUnauthorized());
    }
}