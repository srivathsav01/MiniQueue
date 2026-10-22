package io.github.srivathsav01.miniqueue.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * Manual end-to-end check of the SDK against a REAL, running MiniQueue broker.
 *
 * This is not a JUnit test: it has a main method and you run it by hand while the
 * backend is running. It lives in src/test so it is never shipped inside the library jar.
 *
 * Run it from IntelliJ with the green arrow next to main(). To point it at another
 * broker (for example the Render deployment), add a program argument with the URL.
 */
public class LiveBrokerCheck {

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";

        // Unique names per run, so the check can be repeated without "already exists" errors.
        String run = Long.toString(System.currentTimeMillis());
        String topic = "sdk.check." + run;
        String queue = "sdk-check-" + run;
        String consumerId = "sdk-check-consumer";
        String payload = "{\"hello\":\"sdk\",\"run\":" + run + "}";

        System.out.println("Broker: " + baseUrl);

        // The SDK only covers what producers and consumers do (publish, consume, ack, nack).
        // Creating topics and queues is an admin task, so it is done here with raw HTTP.
        createTopicAndQueue(baseUrl, topic, queue);
        System.out.println("1. Created topic '" + topic + "' and queue '" + queue + "'");

        MiniQueueClient client = MiniQueueClient.builder(baseUrl).build();

        // 2. Publish through the SDK.
        client.publish(topic, payload);
        System.out.println("2. Published: " + payload);

        // 3. Consume through the SDK: the message we just published must come back.
        Optional<ReceivedMessage> received = client.consume(queue, consumerId);
        ReceivedMessage message = received.orElseThrow(
                () -> new AssertionError("Expected a message, but consume returned empty"));
        check(payload.equals(message.payload()), "payload round-trips unchanged");
        check(queue.equals(message.queueName()), "queue name is correct");
        check(message.publishedAt() != null, "published_at is set by the database (Flyway V2)");
        System.out.println("3. Consumed " + message.id() + " published at " + message.publishedAt());

        // 4. Ack it.
        client.ack(message.id(), consumerId);
        System.out.println("4. Acked " + message.id());

        // 5. A second ack must be rejected as a Conflict, because the message is already ACKED.
        try {
            client.ack(message.id(), consumerId);
            throw new AssertionError("Second ack should have failed with Conflict");
        } catch (MiniQueueException.Conflict expected) {
            System.out.println("5. Second ack rejected as expected -> " + expected.getMessage());
        }

        // 6. The queue is now empty, so consume returns Optional.empty() (HTTP 204), not an error.
        check(client.consume(queue, consumerId).isEmpty(), "empty queue returns Optional.empty()");
        System.out.println("6. Consume on the empty queue returned Optional.empty()");

        // 7. A queue that does not exist is a NotFound, and must not be retried.
        try {
            client.consume("does-not-exist-" + run, consumerId);
            throw new AssertionError("Consuming a missing queue should have failed with NotFound");
        } catch (MiniQueueException.NotFound expected) {
            check(!expected.isRetryable(), "NotFound is not retryable");
            System.out.println("7. Missing queue rejected as expected -> " + expected.getMessage());
        }

        System.out.println();
        System.out.println("ALL CHECKS PASSED");
    }

    private static void createTopicAndQueue(String baseUrl, String topic, String queue) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        postJson(http, baseUrl + "/api/v1/topics",
                "{\"name\":\"" + topic + "\"}");
        postJson(http, baseUrl + "/api/v1/queues",
                "{\"name\":\"" + queue + "\",\"topic_name\":\"" + topic + "\"}");
    }

    private static void postJson(HttpClient http, String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Setup call failed: POST " + url
                    + " -> HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("Check failed: " + what);
        }
    }
}