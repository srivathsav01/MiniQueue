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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the client against a tiny stub HTTP server built into the JDK
 * (com.sun.net.httpserver), so no real broker and no extra test libraries are needed.
 * The stub records what the client sent and replies with whatever the test configured.
 */
class MiniQueueClientTest {

    private static final String API_KEY = "mq_test-key-0123456789abcdefghijklmnopqrstuv";
    private final ObjectMapper json = new ObjectMapper();

    private HttpServer server;
    private MiniQueueClient client;

    // What the stub replies with, set by each test.
    private volatile int replyStatus = 200;
    private volatile String replyBody = "{\"status\":200,\"response_body\":\"ok\"}";

    // What the client actually sent, recorded by the stub.
    private volatile String sentMethod;
    private volatile String sentPath;
    private volatile String sentRawPath;
    private volatile String sentBody;
    private volatile String sentAuthorization;
    private volatile String sentInstance;
    private volatile String sentIdempotencyKey;

    @BeforeEach
    void startStubBroker() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            sentMethod = exchange.getRequestMethod();
            sentPath = exchange.getRequestURI().getPath();
            sentRawPath = exchange.getRequestURI().getRawPath();
            sentBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sentAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            sentInstance = exchange.getRequestHeaders().getFirst("MiniQueue-Instance");
            sentIdempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");

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
                .apiKey(API_KEY)
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

        Optional<ReceivedMessage> message = client.consume("email-queue");

        assertTrue(message.isPresent());
        assertEquals(id, message.get().id());
        assertEquals("email-queue", message.get().queueName());
        assertEquals("{\"orderId\":42}", message.get().payload());
        assertEquals(LocalDateTime.of(2026, 10, 16, 12, 30, 45, 123_456_000), message.get().publishedAt());

        assertEquals("POST", sentMethod);
        assertEquals("/api/v1/queues/email-queue/consume", sentPath);
        assertEquals("", sentBody, "the broker takes the consumer's identity from the key, not the body");
    }

    @Test
    void consumeReturnsEmptyWhenNothingIsClaimable() {
        replyStatus = 204;
        replyBody = null;

        assertTrue(client.consume("email-queue").isEmpty());
    }

    @Test
    void consumeEncodesTheQueueNameAsAPathSegment() {
        replyStatus = 204;
        replyBody = null;
        client.consume("my queue/more");
        // The raw path keeps the encoding: a space is %20 (not '+'), and a '/' cannot
        // split the name into two path segments.
        assertEquals("/api/v1/queues/my%20queue%2Fmore/consume", sentRawPath);
    }

    @Test
    void publishSendsTopicAndPayload() throws IOException {
        client.publish("order.placed", "{\"orderId\":42}");

        JsonNode body = json.readTree(sentBody);
        assertEquals("POST", sentMethod);
        assertEquals("/api/v1/topics/order.placed/messages", sentPath);
        assertEquals("{\"orderId\":42}", body.get("payload").asText());
    }

    @Test
    void ackSendsTheMessageIdInThePathAndNoBody() {
        UUID id = UUID.randomUUID();

        client.ack(id);

        assertEquals("/api/v1/messages/" + id + "/ack", sentPath);
        assertEquals("", sentBody);
    }

    @Test
    void nackSendsRequeueFlag() throws IOException {
        UUID id = UUID.randomUUID();
        client.nack(id, false);

        JsonNode body = json.readTree(sentBody);
        assertEquals("/api/v1/messages/" + id + "/nack", sentPath);
        assertFalse(body.get("requeue").asBoolean());
    }

    @Test
    void notFoundIsNotRetryableAndCarriesTheBrokerMessage() {
        replyStatus = 404;
        replyBody = "{\"status\":404,\"message\":\"No Queue found with the name missing\"}";

        MiniQueueException.NotFound e = assertThrows(MiniQueueException.NotFound.class,
                () -> client.consume("missing"));

        assertEquals(404, e.statusCode());
        assertFalse(e.isRetryable());
        assertEquals("HTTP 404: No Queue found with the name missing", e.getMessage());
    }

    @Test
    void conflictOnAckIsNotRetryable() {
        replyStatus = 409;
        replyBody = "{\"status\":409,\"message\":\"The message is not in unacked state\"}";

        MiniQueueException.Conflict e = assertThrows(MiniQueueException.Conflict.class,
                () -> client.ack(UUID.randomUUID()));

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
        assertThrows(IllegalArgumentException.class, () -> client.consume(" "));
        assertThrows(IllegalArgumentException.class, () -> MiniQueueClient.builder("localhost:8080"));
    }

    @Test
    void oversizedPayloadIsNotRetryable() {
        replyStatus = 413;
        replyBody = "{\"status\":413,\"message\":\"Payload is 300000 bytes; the limit is 262144\"}";

        MiniQueueException.PayloadTooLarge e = assertThrows(MiniQueueException.PayloadTooLarge.class,
                () -> client.publish("order.placed", "{}"));

        assertFalse(e.isRetryable());
    }

    @Test
    void everyRequestCarriesTheApiKey() {
        client.publish("order.placed", "{}");
        assertEquals("Bearer " + API_KEY, sentAuthorization);

        client.ack(UUID.randomUUID());
        assertEquals("Bearer " + API_KEY, sentAuthorization);
    }

    @Test
    void theInstanceHeaderIsSentOnlyWhenConfigured() {
        client.ack(UUID.randomUUID());
        assertNull(sentInstance);

        MiniQueueClient withInstance = MiniQueueClient.builder("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey(API_KEY)
                .instanceId("pod-2")
                .retryPolicy(RetryPolicy.none())
                .build();
        withInstance.ack(UUID.randomUUID());
        assertEquals("pod-2", sentInstance);
    }

    @Test
    void buildingWithoutAnApiKeyFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> MiniQueueClient.builder("http://localhost:8080").build());
        assertTrue(e.getMessage().contains("apiKey"));
    }

    @Test
    void unauthorizedIsNotRetryableAndNeverEchoesTheKey() {
        replyStatus = 401;
        replyBody = "{\"status\":401,\"message\":\"Missing, invalid or revoked API key\"}";

        MiniQueueException.Unauthorized e = assertThrows(MiniQueueException.Unauthorized.class,
                () -> client.publish("order.placed", "{}"));

        assertFalse(e.isRetryable());
        assertFalse(e.getMessage().contains(API_KEY), "an exception message can end up in logs");
    }

    @Test
    void forbiddenIsNotRetryable() {
        replyStatus = 403;
        replyBody = "{\"status\":403,\"message\":\"This API key is not allowed to do this\"}";

        MiniQueueException.Forbidden e = assertThrows(MiniQueueException.Forbidden.class,
                () -> client.consume("someone-elses-queue"));

        assertEquals(403, e.statusCode());
        assertFalse(e.isRetryable());
    }

    @Test
    void everyPublishSendsItsOwnIdempotencyKey() {
        client.publish("order.placed", "{}");
        String first = sentIdempotencyKey;
        client.publish("order.placed", "{}");

        assertNotNull(first);
        UUID.fromString(first); // a random UUID
        assertNotEquals(first, sentIdempotencyKey, "two publishes are two different messages");
    }

    @Test
    void aCallerChosenKeyIsSentAsGiven() {
        client.publish("order.placed", "{}", "order-42");
        assertEquals("order-42", sentIdempotencyKey);
    }

    @Test
    void publishReturnsTheBrokersMessageIds() {
        UUID id = UUID.randomUUID();
        replyBody = "{\"status\":200,\"response_body\":{\"message_ids\":[\"" + id + "\"],\"duplicate\":true}}";

        PublishResult result = client.publish("order.placed", "{}");

        assertEquals(List.of(id), result.messageIds());
        assertTrue(result.duplicate());
    }

    @Test
    void aRetriedPublishReusesItsKey() throws IOException {
        // A broker that fails the first attempt, then succeeds, recording each attempt's key.
        List<String> keys = new CopyOnWriteArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        HttpServer flaky = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        flaky.createContext("/", exchange -> {
            keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            exchange.getRequestBody().readAllBytes();
            int status = attempts.getAndIncrement() == 0 ? 503 : 200;
            byte[] body = "{\"status\":200,\"response_body\":{\"message_ids\":[],\"duplicate\":false}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        flaky.start();
        try {
            MiniQueueClient retrying = MiniQueueClient.builder("http://127.0.0.1:" + flaky.getAddress().getPort())
                    .apiKey(API_KEY)
                    .retryPolicy(RetryPolicy.of(3, Duration.ofMillis(1), Duration.ofMillis(5)))
                    .build();

            retrying.publish("order.placed", "{}");

            assertEquals(2, keys.size(), "one failure, one retry");
            assertEquals(keys.get(0), keys.get(1), "the retry must carry the same key, or it would store a copy");
        } finally {
            flaky.stop(0);
        }
    }
}