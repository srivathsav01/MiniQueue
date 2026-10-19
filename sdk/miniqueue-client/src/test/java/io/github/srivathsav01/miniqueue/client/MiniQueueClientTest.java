package io.github.srivathsav01.miniqueue.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the client against a tiny stub HTTP server built into the JDK
 * (com.sun.net.httpserver), so no real broker and no extra test libraries are needed.
 * The stub records what the client sent and replies with whatever the test configured.
 */
class MiniQueueClientTest {

    private final ObjectMapper json = new ObjectMapper();

    private HttpServer server;
    private MiniQueueClient client;

    // What the stub replies with, set by each test.
    private volatile int replyStatus = 200;
    private volatile String replyBody = "{\"status\":200,\"response_body\":\"ok\"}";

    // What the client actually sent, recorded by the stub.
    private volatile String sentMethod;
    private volatile String sentPath;
    private volatile String sentQuery;
    private volatile String sentBody;

    @BeforeEach
    void startStubBroker() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            sentMethod = exchange.getRequestMethod();
            sentPath = exchange.getRequestURI().getPath();
            sentQuery = exchange.getRequestURI().getRawQuery();
            sentBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            byte[] bytes = replyBody == null ? new byte[0] : replyBody.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0) {
                exchange.sendResponseHeaders(replyStatus, -1); // -1 = no body, required for 204
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(replyStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        // Retries are tested in RetryPolicyTest. Here each call must hit the stub exactly once,
        // and the 500 / connection-failure tests must not wait through backoff sleeps.
        client = MiniQueueClient.builder("http://127.0.0.1:" + server.getAddress().getPort() + "/")
                .retryPolicy(RetryPolicy.none())
                .build();
    }

    @AfterEach
    void stopStubBroker() {
        server.stop(0);
    }

    @Test
    void consumeReturnsTheClaimedMessage() {
        UUID id = UUID.randomUUID();
        replyBody = """
                {"status":200,"response_body":{
                  "message_id":"%s","queue_name":"email-queue",
                  "payload":"{\\"orderId\\":42}","published_at":"2026-10-16T12:30:45.123456"}}
                """.formatted(id);

        Optional<ReceivedMessage> message = client.consume("email-queue", "email-service");

        assertTrue(message.isPresent());
        assertEquals(id, message.get().id());
        assertEquals("email-queue", message.get().queueName());
        assertEquals("{\"orderId\":42}", message.get().payload());
        assertEquals(LocalDateTime.of(2026, 10, 16, 12, 30, 45, 123_456_000), message.get().publishedAt());

        assertEquals("GET", sentMethod);
        assertEquals("/consumer/consumeMessage", sentPath);
        assertEquals("queue_name=email-queue&consumer_id=email-service", sentQuery);
    }

    @Test
    void consumeReturnsEmptyWhenNothingIsClaimable() {
        replyStatus = 204;
        replyBody = null;

        assertTrue(client.consume("email-queue", "email-service").isEmpty());
    }

    @Test
    void consumeUrlEncodesNames() {
        replyStatus = 204;
        replyBody = null;

        client.consume("my queue&more", "worker 1");

        assertEquals("queue_name=my+queue%26more&consumer_id=worker+1", sentQuery);
    }

    @Test
    void publishSendsTopicAndPayload() throws IOException {
        client.publish("order.placed", "{\"orderId\":42}");

        JsonNode body = json.readTree(sentBody);
        assertEquals("POST", sentMethod);
        assertEquals("/messages/publishMessage", sentPath);
        assertEquals("order.placed", body.get("topic_name").asText());
        assertEquals("{\"orderId\":42}", body.get("payload").asText());
    }

    @Test
    void ackSendsMessageIdAndConsumerId() throws IOException {
        UUID id = UUID.randomUUID();

        client.ack(id, "email-service");

        JsonNode body = json.readTree(sentBody);
        assertEquals("/consumer/ackMessage", sentPath);
        assertEquals(id.toString(), body.get("message_id").asText());
        assertEquals("email-service", body.get("consumer_id").asText());
    }

    @Test
    void nackSendsRequeueFlag() throws IOException {
        client.nack(UUID.randomUUID(), "email-service", false);

        JsonNode body = json.readTree(sentBody);
        assertEquals("/consumer/nackMessage", sentPath);
        assertFalse(body.get("requeue").asBoolean());
    }

    @Test
    void notFoundIsNotRetryableAndCarriesTheBrokerMessage() {
        replyStatus = 404;
        replyBody = "{\"status\":404,\"message\":\"No Queue found with the name missing\"}";

        MiniQueueException.NotFound e = assertThrows(MiniQueueException.NotFound.class,
                () -> client.consume("missing", "email-service"));

        assertEquals(404, e.statusCode());
        assertFalse(e.isRetryable());
        assertEquals("HTTP 404: No Queue found with the name missing", e.getMessage());
    }

    @Test
    void conflictOnAckIsNotRetryable() {
        replyStatus = 409;
        replyBody = "{\"status\":409,\"message\":\"The message is not in unacked state\"}";

        MiniQueueException.Conflict e = assertThrows(MiniQueueException.Conflict.class,
                () -> client.ack(UUID.randomUUID(), "email-service"));

        assertFalse(e.isRetryable());
    }

    @Test
    void serverErrorIsRetryable() {
        replyStatus = 500;
        replyBody = "{\"status\":500,\"message\":\"An unexpected error occurred\"}";

        MiniQueueException.ServerError e = assertThrows(MiniQueueException.ServerError.class,
                () -> client.publish("order.placed", "{}"));

        assertEquals(500, e.statusCode());
        assertTrue(e.isRetryable());
    }

    @Test
    void unreachableBrokerIsAConnectionFailureAndRetryable() {
        server.stop(0); // nothing is listening on that port any more

        MiniQueueException.ConnectionFailed e = assertThrows(MiniQueueException.ConnectionFailed.class,
                () -> client.publish("order.placed", "{}"));

        assertEquals(0, e.statusCode());
        assertTrue(e.isRetryable());
    }

    @Test
    void blankArgumentsAreRejectedBeforeAnyHttpCall() {
        assertThrows(IllegalArgumentException.class, () -> client.consume(" ", "email-service"));
        assertThrows(IllegalArgumentException.class, () -> MiniQueueClient.builder("localhost:8080"));
    }
}