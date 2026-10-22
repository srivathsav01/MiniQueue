package io.github.srivathsav01.miniqueue.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain-Java client for the MiniQueue broker.
 *
 * <pre>{@code
 * MiniQueueClient client = MiniQueueClient.builder("http://localhost:8080").build();
 *
 * client.publish("order.placed", "{\"orderId\": 42}");
 *
 * client.consume("email-queue", "email-service").ifPresent(message -> {
 *     sendEmail(message.payload());
 *     client.ack(message.id(), "email-service");
 * });
 * }</pre>
 *
 * Thread-safe: create one instance per broker and share it. It holds a pooled HTTP
 * client, so creating one per request wastes connections.
 *
 * Retries: failures that may succeed on a second try (5xx, connection errors) are retried
 * according to the {@link RetryPolicy}, by default up to 3 attempts with exponential
 * backoff. 4xx failures are never retried. For a continuous consumer loop on top of this
 * client, see {@link MiniQueueConsumer}.
 */
public final class MiniQueueClient {

    /** Every broker endpoint lives under this version prefix. */
    private static final String API = "/api/v1";

    static final String USER_AGENT = "miniqueue-java-client/0.1.0";

    private final String baseUrl;
    private final Duration requestTimeout;
    private final RetryPolicy retryPolicy;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    private MiniQueueClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.requestTimeout = builder.requestTimeout;
        this.retryPolicy = builder.retryPolicy;
        this.http = HttpClient.newBuilder()
                .connectTimeout(builder.connectTimeout)
                .build();
    }

    /** @param baseUrl the broker's root URL, for example {@code http://localhost:8080} */
    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    /**
     * Publishes a message to a topic. The broker copies it into every queue bound to
     * that topic, atomically: either every queue gets it or none do.
     *
     * @throws MiniQueueException.NotFound   if the topic does not exist
     * @throws MiniQueueException.BadRequest if no queue is bound to the topic
     */
    public void publish(String topic, String payload) {
        requireText(topic, "topic");
        Objects.requireNonNull(payload, "payload");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payload", payload);
        send(post(API + "/topics/" + pathSegment(topic) + "/messages", body));
    }

    /**
     * Claims the oldest available message in a queue for this consumer.
     *
     * Returns empty when nothing is claimable right now. That is a normal result, not an
     * error: either the queue is empty, or every remaining message is momentarily held
     * by other consumers. Poll again later.
     *
     * The returned message must be acked or nacked by the same consumerId, otherwise
     * the broker redelivers it after its ack timeout.
     *
     * @throws MiniQueueException.NotFound if the queue does not exist
     */
    public Optional<ReceivedMessage> consume(String queue, String consumerId) {
        requireText(queue, "queue");
        requireText(consumerId, "consumerId");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consumer_id", consumerId);
        HttpResponse<String> response = send(post(API + "/queues/" + pathSegment(queue) + "/consume", body));

        if (response.statusCode() == 204) {
            return Optional.empty();
        }
        return Optional.of(toReceivedMessage(readTree(response).path("response_body")));
    }

    /**
     * Confirms the message was processed. The broker marks it ACKED and never delivers
     * it again.
     *
     * @throws MiniQueueException.Conflict if the message is no longer UNACKED, or now
     *                                     belongs to another consumer (it was redelivered
     *                                     after the ack timeout)
     * @throws MiniQueueException.NotFound if no message has this id
     */
    public void ack(UUID messageId, String consumerId) {
        Objects.requireNonNull(messageId, "messageId");
        requireText(consumerId, "consumerId");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consumer_id", consumerId);
        send(post(API + "/messages/" + messageId + "/ack", body));
    }

    /**
     * Rejects the message.
     *
     * @param requeue true puts it back to PENDING for another attempt; false moves it
     *                straight to the dead-letter queue
     * @throws MiniQueueException.Conflict if the message is no longer UNACKED, or now
     *                                     belongs to another consumer
     * @throws MiniQueueException.NotFound if no message has this id
     */
    public void nack(UUID messageId, String consumerId, boolean requeue) {
        Objects.requireNonNull(messageId, "messageId");
        requireText(consumerId, "consumerId");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consumer_id", consumerId);
        body.put("requeue", requeue);
        send(post(API + "/messages/" + messageId + "/nack", body));
    }

    // ---------------------------------------------------------------------------------
    // HTTP plumbing
    // ---------------------------------------------------------------------------------

    private HttpRequest post(String path, Map<String, Object> body) {
        String jsonBody;
        try {
            jsonBody = json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            // Only strings, booleans and UUID strings go in here, so this cannot really happen.
            throw new IllegalStateException("Could not serialise request body", e);
        }
        return newRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest.Builder newRequest(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
    }

    /**
     * Sends the request, retrying retryable failures per the RetryPolicy. HttpRequest is
     * immutable and its body publisher can be replayed, so the same object is resent.
     *
     * A retry can repeat work the broker already did if only the response was lost: a
     * retried publish may store a second copy, and a retried ack may come back as a 409
     * Conflict because the first attempt already acked it. Both are expected under
     * at-least-once delivery.
     */
    private HttpResponse<String> send(HttpRequest request) {
        return retryPolicy.execute(() -> sendOnce(request));
    }

    /** One HTTP call; turns every non-2xx response into the matching exception. */
    private HttpResponse<String> sendOnce(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Covers connection refused, DNS failures and timeouts (HttpTimeoutException is an IOException).
            throw new MiniQueueException.ConnectionFailed(
                    "Could not reach MiniQueue at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            // Restore the flag so the caller's thread still knows it was interrupted.
            Thread.currentThread().interrupt();
            throw new MiniQueueException("Interrupted while calling MiniQueue", 0, e);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response;
        }

        String message = errorMessage(status, response.body());
        throw switch (status) {
            case 400 -> new MiniQueueException.BadRequest(message);
            case 404 -> new MiniQueueException.NotFound(message);
            case 409 -> new MiniQueueException.Conflict(message);
            case 413 -> new MiniQueueException.PayloadTooLarge(message);
            default -> status >= 500
                    ? new MiniQueueException.ServerError(message, status)
                    : new MiniQueueException(message, status, null);
        };
    }

    /** Uses the broker's own error message from the response envelope when there is one. */
    private String errorMessage(int status, String body) {
        if (body != null && !body.isBlank()) {
            try {
                String brokerMessage = json.readTree(body).path("message").asText(null);
                if (brokerMessage != null && !brokerMessage.isBlank()) {
                    return "HTTP " + status + ": " + brokerMessage;
                }
            } catch (IOException ignored) {
                // Not JSON (a proxy error page, for example). Fall back to the raw text below.
            }
            String raw = body.length() > 200 ? body.substring(0, 200) + "..." : body;
            return "HTTP " + status + ": " + raw;
        }
        return "HTTP " + status;
    }

    private JsonNode readTree(HttpResponse<String> response) {
        try {
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new MiniQueueException(
                    "Unexpected non-JSON response from MiniQueue", response.statusCode(), e);
        }
    }

    /**
     * Maps fields by hand instead of using Jackson annotations, so the public record stays
     * free of serialisation details and no extra date/time module is needed.
     */
    private ReceivedMessage toReceivedMessage(JsonNode node) {
        try {
            String publishedAt = node.path("published_at").asText(null);
            return new ReceivedMessage(
                    UUID.fromString(node.path("message_id").asText()),
                    node.path("queue_name").asText(null),
                    node.path("payload").asText(null),
                    publishedAt == null ? null : LocalDateTime.parse(publishedAt));
        } catch (RuntimeException e) {
            throw new MiniQueueException("Unexpected consume response from MiniQueue: " + node, 200, e);
        }
    }

    /**
     * Encodes a topic or queue name for use as one URL path segment. URLEncoder is meant
     * for query strings and turns a space into '+', which in a path is a literal plus,
     * so spaces are re-encoded as %20. The broker rejects such names anyway; this only
     * guarantees the request reaches it and gets a clean 400 instead of a wrong URL.
     */
    private static String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    // ---------------------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------------------

    public static final class Builder {

        private final String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);
        private RetryPolicy retryPolicy = RetryPolicy.defaults();

        private Builder(String baseUrl) {
            requireText(baseUrl, "baseUrl");
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                throw new IllegalArgumentException("baseUrl must start with http:// or https://");
            }
            // Strip trailing slashes so "http://host/" + "/messages" never becomes "//messages".
            this.baseUrl = baseUrl.replaceAll("/+$", "");
        }

        /** How long to wait to open a TCP connection. Default 5 seconds. */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = requirePositive(timeout, "connectTimeout");
            return this;
        }

        /** How long to wait for a full response once connected. Default 10 seconds. */
        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = requirePositive(timeout, "requestTimeout");
            return this;
        }

        /** How failed calls are retried. Default {@link RetryPolicy#defaults()}; use {@link RetryPolicy#none()} to disable. */
        public Builder retryPolicy(RetryPolicy policy) {
            this.retryPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public MiniQueueClient build() {
            return new MiniQueueClient(this);
        }

        private static Duration requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}