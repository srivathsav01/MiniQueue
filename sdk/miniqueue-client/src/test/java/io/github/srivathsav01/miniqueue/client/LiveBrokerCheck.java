package io.github.srivathsav01.miniqueue.client;

import java.util.Optional;

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
        String payload = "{\"hello\":\"sdk\",\"run\":" + run + "}";

        System.out.println("Broker: " + baseUrl);

        LiveSetup.createTopicAndQueue(baseUrl, topic, queue);
        String apiKey = LiveSetup.registerClient(baseUrl, "sdk-check-" + run, "publish:" + topic, "consume:" + queue);
        System.out.println("1. Created topic '" + topic + "', queue '" + queue + "' and a client scoped to both");

        MiniQueueClient client = MiniQueueClient.builder(baseUrl).apiKey(apiKey).build();

        // 2. Publish through the SDK.
        client.publish(topic, payload);
        System.out.println("2. Published: " + payload);

        // 3. Consume through the SDK: the message we just published must come back.
        Optional<ReceivedMessage> received = client.consume(queue);
        ReceivedMessage message = received.orElseThrow(
                () -> new AssertionError("Expected a message, but consume returned empty"));
        check(payload.equals(message.payload()), "payload round-trips unchanged");
        check(queue.equals(message.queueName()), "queue name is correct");
        check(message.publishedAt() != null, "published_at is set by the database (Flyway V2)");
        System.out.println("3. Consumed " + message.id() + " published at " + message.publishedAt());

        // 4. Ack it.
        client.ack(message.id());
        System.out.println("4. Acked " + message.id());

        // 5. A second ack must be rejected as a Conflict, because the message is already ACKED.
        try {
            client.ack(message.id());
            throw new AssertionError("Second ack should have failed with Conflict");
        } catch (MiniQueueException.Conflict expected) {
            System.out.println("5. Second ack rejected as expected -> " + expected.getMessage());
        }

        // 6. The queue is now empty, so consume returns Optional.empty() (HTTP 204), not an error.
        check(client.consume(queue).isEmpty(), "empty queue returns Optional.empty()");
        System.out.println("6. Consume on the empty queue returned Optional.empty()");

        // 7. A topic outside this key's scopes is Forbidden (403), and must not be retried.
        try {
            client.publish("not-my-topic-" + run, payload);
            throw new AssertionError("Publishing outside the key's scopes should have failed with Forbidden");
        } catch (MiniQueueException.Forbidden expected) {
            check(!expected.isRetryable(), "Forbidden is not retryable");
            System.out.println("7. Out-of-scope publish rejected as expected -> " + expected.getMessage());
        }

        // 8. A key the broker never issued is Unauthorized (401).
        MiniQueueClient impostor = MiniQueueClient.builder(baseUrl)
                .apiKey("mq_never-issued-0123456789abcdefghijklmnop")
                .build();
        try {
            impostor.publish(topic, payload);
            throw new AssertionError("A made-up key should have failed with Unauthorized");
        } catch (MiniQueueException.Unauthorized expected) {
            check(!expected.isRetryable(), "Unauthorized is not retryable");
            System.out.println("8. Made-up key rejected as expected -> " + expected.getMessage());
        }

        System.out.println();
        System.out.println("ALL CHECKS PASSED");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("Check failed: " + what);
        }
    }

}