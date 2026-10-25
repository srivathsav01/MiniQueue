package com.sri.miniqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.service.ClientService;
import com.sri.miniqueue.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sends real HTTP requests through the full Spring Security filter chain (MockMvc), against
 * a real Postgres, to prove who may do what.
 */
@SpringBootTest()
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {

    @Autowired
    private ClientService clientService;

    private String adminKey;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MessageService messageService;

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
        adminKey = clientService.createClient("test-admin-" + suffix, List.of("admin")).getApiKey();
    }

    @Test
    void publishWithoutAKeyIs401() throws Exception {
        publish(null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void aKeyThatWasNeverIssuedIs401() throws Exception {
        publish("mq_this-key-was-never-issued-0123456789abcdef").andExpect(status().isUnauthorized());
    }

    @Test
    void aValidKeyWithoutTheMatchingScopeIs403() throws Exception {
        String key = createClient("p-" + suffix, "publish:some-other-topic", "consume:" + queue);
        publish(key).andExpect(status().isForbidden());
    }

    @Test
    void exactAndWildcardScopesCanPublish() throws Exception {
        publish(createClient("exact-" + suffix, "publish:" + topic)).andExpect(status().isOk());
        publish(createClient("wild-" + suffix, "publish:*")).andExpect(status().isOk());
    }

    @Test
    void onlyAdminsCanRegisterClients() throws Exception {
        String key = createClient("p-" + suffix, "publish:*");
        mvc.perform(post("/api/v1/clients")
                        .header(AUTHORIZATION, "Bearer " + key)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"sneaky-" + suffix + "\",\"scopes\":[\"admin\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void consumerIdentityComesFromTheKeyNotTheRequest() throws Exception {
        publish(createClient("producer-" + suffix, "publish:" + topic)).andExpect(status().isOk());
        String alice = createClient("alice-" + suffix, "consume:" + queue);
        String bob = createClient("bob-" + suffix, "consume:" + queue);

        String body = mvc.perform(post("/api/v1/queues/" + queue + "/consume").header(AUTHORIZATION, "Bearer " + alice))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String messageId = json.readTree(body).path("response_body").path("message_id").asText();

        // Bob has a valid key with the same scope, but Alice holds this message.
        mvc.perform(post("/api/v1/messages/" + messageId + "/ack").header(AUTHORIZATION, "Bearer " + bob))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/messages/" + messageId + "/ack").header(AUTHORIZATION, "Bearer " + alice))
                .andExpect(status().isOk());
    }

    @Test
    void rotatedAndRevokedKeysStopWorking() throws Exception {
        String name = "rotating-" + suffix;
        String oldKey = createClient(name, "publish:" + topic);
        publish(oldKey).andExpect(status().isOk());

        String rotated = mvc.perform(post("/api/v1/clients/" + name + "/rotate").header(AUTHORIZATION, "Bearer " + adminKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newKey = json.readTree(rotated).path("response_body").path("api_key").asText();

        publish(oldKey).andExpect(status().isUnauthorized());
        publish(newKey).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/clients/" + name).header(AUTHORIZATION, "Bearer " + adminKey))
                .andExpect(status().isOk());
        publish(newKey).andExpect(status().isUnauthorized());
    }

    private String createClient(String name, String... scopes) throws Exception {
        String request = json.writeValueAsString(Map.of("name", name, "scopes", List.of(scopes)));
        String response = mvc.perform(post("/api/v1/clients")
                        .header(AUTHORIZATION, "Bearer " + adminKey)
                        .contentType(APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("response_body").path("api_key").asText();
    }

    private ResultActions publish(String key) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/topics/" + topic + "/messages")
                .contentType(APPLICATION_JSON)
                .content("{\"payload\":\"hello\"}");
        if (key != null) {
            request.header(AUTHORIZATION, "Bearer " + key);
        }
        return mvc.perform(request);
    }
}