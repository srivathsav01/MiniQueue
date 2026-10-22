package io.github.srivathsav01.miniqueue.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal in-memory imitation of the broker's consumer endpoints, for testing the
 * consumer loop without Docker or a database. It hands out queued payloads, answers 204
 * when empty, and records every ack and nack.
 */
final class StubBroker implements AutoCloseable {

    final List<UUID> acked = new CopyOnWriteArrayList<>();
    final List<UUID> nacked = new CopyOnWriteArrayList<>();
    final AtomicInteger consumeCalls = new AtomicInteger();

    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private final Map<UUID, String> delivered = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpServer server;

    StubBroker() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor); // serve concurrent consumers in parallel, like the real broker

        // Paths carry the queue name or message id, so match on prefixes and suffixes.
        server.createContext("/api/v1/queues/", exchange -> {
            if (!exchange.getRequestURI().getPath().endsWith("/consume")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            consumeCalls.incrementAndGet();
            String payload = pending.poll();
            if (payload == null) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            UUID id = UUID.randomUUID();
            delivered.put(id, payload);
            reply(exchange, json.writeValueAsString(Map.of(
                    "status", 200,
                    "response_body", Map.of(
                            "message_id", id.toString(),
                            "queue_name", "test-queue",
                            "payload", payload,
                            "published_at", "2026-10-19T10:00:00"))));
        });
        server.createContext("/api/v1/messages/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/ack")) {
                acked.add(messageIdFrom(path));
                reply(exchange, "{\"status\":200,\"response_body\":\"Message acknowledged\"}");
            } else if (path.endsWith("/nack")) {
                nacked.add(messageIdFrom(path));
                reply(exchange, "{\"status\":200,\"response_body\":\"Message reset to Pending\"}");
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void enqueue(String... payloads) {
        pending.addAll(List.of(payloads));
    }

    String payloadOf(UUID id) {
        return delivered.get(id);
    }

    /** "/api/v1/messages/{id}/ack" -> {id} */
    private static UUID messageIdFrom(String path) {
        String[] parts = path.split("/");
        return UUID.fromString(parts[parts.length - 2]);
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}