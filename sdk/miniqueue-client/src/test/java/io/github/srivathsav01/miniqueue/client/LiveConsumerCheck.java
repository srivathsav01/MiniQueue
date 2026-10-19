package io.github.srivathsav01.miniqueue.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manual end-to-end check of MiniQueueConsumer against a REAL, running broker.
 *
 * Publishes 20 normal messages plus one "poison" message whose handler always fails,
 * then runs a 4-worker consumer and checks that:
 *   - every normal message is handled and acked exactly once
 *   - the poison message is retried, then stops arriving once the broker moves it to
 *     the dead-letter queue (after miniqueue.redelivery.max-retries failed attempts)
 *
 * Run with the green arrow next to main() while the backend is running.
 */
public class LiveConsumerCheck {

    private static final int NORMAL_MESSAGES = 20;

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        String run = Long.toString(System.currentTimeMillis());
        String topic = "sdk.consumer." + run;
        String queue = "sdk-consumer-" + run;

        createTopicAndQueue(baseUrl, topic, queue);
        MiniQueueClient client = MiniQueueClient.builder(baseUrl).build();

        for (int i = 1; i <= NORMAL_MESSAGES; i++) {
            client.publish(topic, "order-" + i);
        }
        client.publish(topic, "poison");
        System.out.println("Published " + NORMAL_MESSAGES + " normal messages and 1 poison message to " + queue);

        Set<String> handled = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicates = new AtomicInteger();
        AtomicInteger poisonAttempts = new AtomicInteger();

        MessageHandler handler = message -> {
            if (message.payload().equals("poison")) {
                int attempt = poisonAttempts.incrementAndGet();
                System.out.println("   poison attempt " + attempt + " -> throwing");
                throw new IllegalStateException("simulated permanent failure");
            }
            if (!handled.add(message.payload())) {
                duplicates.incrementAndGet();
            }
        };

        try (MiniQueueConsumer consumer = MiniQueueConsumer
                .builder(client, queue, "sdk-live-consumer", handler)
                .concurrency(4)
                .pollInterval(Duration.ofMillis(50), Duration.ofMillis(500))
                .build()
                .start()) {

            // Wait for the normal messages, then give the poison message time to exhaust its retries.
            long deadline = System.currentTimeMillis() + 30_000;
            while (handled.size() < NORMAL_MESSAGES && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            Thread.sleep(3_000);
        }

        int poisonSeen = poisonAttempts.get();
        System.out.println();
        System.out.println("Normal messages handled: " + handled.size() + "/" + NORMAL_MESSAGES
                + ", duplicates: " + duplicates.get());
        System.out.println("Poison message attempts: " + poisonSeen);

        check(handled.size() == NORMAL_MESSAGES, "every normal message was handled");
        check(duplicates.get() == 0, "no normal message was handled twice");
        check(poisonSeen >= 1, "the poison message was delivered");
        check(poisonSeen < 10, "the poison message stopped being redelivered (it went to the DLQ)");

        System.out.println();
        System.out.println("ALL CHECKS PASSED. Open the dashboard's Dead Letter Queue: the 'poison' message from "
                + queue + " should be there.");
    }

    private static void createTopicAndQueue(String baseUrl, String topic, String queue) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        post(http, baseUrl + "/topics/createTopic", "{\"name\":\"" + topic + "\"}");
        post(http, baseUrl + "/queues/createQueue", "{\"name\":\"" + queue + "\",\"topic_name\":\"" + topic + "\"}");
    }

    private static void post(HttpClient http, String url, String body) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Setup failed: " + url + " -> " + response.statusCode() + " " + response.body());
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("Check failed: " + what);
        }
        System.out.println("ok  " + what);
    }
}