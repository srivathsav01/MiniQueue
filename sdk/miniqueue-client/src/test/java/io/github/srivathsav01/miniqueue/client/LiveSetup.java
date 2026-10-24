package io.github.srivathsav01.miniqueue.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Admin-side setup for the live checks: topics, queues and API keys. The SDK only covers
 * what producers and consumers do, so this uses raw HTTP, the way an operator would.
 *
 * Needs the broker's admin key in the MINIQUEUE_ADMIN_KEY environment variable (the same
 * value as the backend's MINIQUEUE_BOOTSTRAP_ADMIN_KEY). In IntelliJ: Run, Edit
 * Configurations, the check's run configuration, Environment variables.
 */
final class LiveSetup {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    private LiveSetup() {
    }

    static String adminKey() {
        String key = System.getenv("MINIQUEUE_ADMIN_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set MINIQUEUE_ADMIN_KEY to the broker's bootstrap admin key");
        }
        return key;
    }

    static void createTopicAndQueue(String baseUrl, String topic, String queue) throws Exception {
        post(baseUrl + "/api/v1/topics", null, Map.of("name", topic));
        post(baseUrl + "/api/v1/queues", null, Map.of("name", queue, "topic_name", topic));
    }

    /** Registers a client with the given scopes and returns its API key (shown only this once). */
    static String registerClient(String baseUrl, String name, String... scopes) throws Exception {
        String body = post(baseUrl + "/api/v1/clients", adminKey(), Map.of("name", name, "scopes", List.of(scopes)));
        return JSON.readTree(body).path("response_body").path("api_key").asText();
    }

    private static String post(String url, String bearer, Map<String, ?> body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Setup call failed: POST " + url
                    + " -> HTTP " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }
}