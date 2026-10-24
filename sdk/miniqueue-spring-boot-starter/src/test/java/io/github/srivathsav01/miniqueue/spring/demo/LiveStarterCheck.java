package io.github.srivathsav01.miniqueue.spring.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.srivathsav01.miniqueue.client.MiniQueueClient;
import io.github.srivathsav01.miniqueue.spring.MiniQueueListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A real Spring Boot application that uses the starter exactly as a user would, run
 * against a REAL, running MiniQueue broker. Run main() while the backend is running.
 *
 * Note what this class does NOT contain: no client construction, no consumer loop, no
 * thread management. Only a property (the broker URL) and an annotated method.
 */
@SpringBootApplication
public class LiveStarterCheck {

    private static final int MESSAGES = 10;

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        String run = Long.toString(System.currentTimeMillis());
        String topic = "starter.demo." + run;
        String queue = "starter-demo-" + run;

        // The listener's queue name comes from this property: @MiniQueueListener(queue = "${demo.queue}").
        // The queue must exist before the listener starts polling it, so create it first.
        System.setProperty("demo.queue", queue);
        createTopicAndQueue(baseUrl, topic, queue);
        String apiKey = registerClient(baseUrl, "starter-demo-" + run, "publish:" + topic, "consume:" + queue);

        ConfigurableApplicationContext context = SpringApplication.run(LiveStarterCheck.class,
                "--miniqueue.client.base-url=" + baseUrl,
                "--miniqueue.client.api-key=" + apiKey,
                "--spring.application.name=starter-demo");

        // The auto-configured client bean, used here only to publish test messages.
        MiniQueueClient client = context.getBean(MiniQueueClient.class);
        for (int i = 1; i <= MESSAGES; i++) {
            client.publish(topic, "hello-" + i);
        }
        System.out.println(">>> Published " + MESSAGES + " messages to " + topic);

        DemoListener listener = context.getBean(DemoListener.class);
        long deadline = System.currentTimeMillis() + 20_000;
        while (listener.received.size() < MESSAGES && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        context.close(); // stops the listener gracefully, exactly as an application shutdown would

        System.out.println();
        System.out.println(">>> Listener received " + listener.received.size() + "/" + MESSAGES);
        if (listener.received.size() != MESSAGES) {
            throw new AssertionError("Not every message reached the @MiniQueueListener method");
        }
        System.out.println(">>> ALL CHECKS PASSED");
    }

    /** A user's listener bean. Component scanning finds it; the starter does the rest. */
    @Component
    static class DemoListener {

        final Set<String> received = ConcurrentHashMap.newKeySet();

        @MiniQueueListener(queue = "${demo.queue}", concurrency = 2)
        public void onMessage(String payload) {
            System.out.println(">>> " + Thread.currentThread().getName() + " handled " + payload);
            received.add(payload);
        }
    }

    private static void createTopicAndQueue(String baseUrl, String topic, String queue) throws Exception {
        post(baseUrl + "/api/v1/topics", null, "{\"name\":\"" + topic + "\"}");
        post(baseUrl + "/api/v1/queues", null, "{\"name\":\"" + queue + "\",\"topic_name\":\"" + topic + "\"}");
    }

    /** Registers a client with the admin key from MINIQUEUE_ADMIN_KEY and returns its new API key. */
    private static String registerClient(String baseUrl, String name, String... scopes) throws Exception {
        String adminKey = System.getenv("MINIQUEUE_ADMIN_KEY");
        if (adminKey == null || adminKey.isBlank()) {
            throw new IllegalStateException("Set MINIQUEUE_ADMIN_KEY to the broker's bootstrap admin key");
        }
        ObjectMapper json = new ObjectMapper();
        String body = json.writeValueAsString(Map.of("name", name, "scopes", List.of(scopes)));
        String response = post(baseUrl + "/api/v1/clients", adminKey, body);
        return json.readTree(response).path("response_body").path("api_key").asText();
    }

    private static String post(String url, String bearer, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Setup failed: " + url + " -> " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }
}