package io.github.srivathsav01.miniqueue.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.srivathsav01.miniqueue.client.MiniQueueClient;
import io.github.srivathsav01.miniqueue.client.ReceivedMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * ApplicationContextRunner starts a small, real Spring context with only our
 * auto-configuration in it, which is far faster than @SpringBootTest. Listeners talk to
 * TinyBroker, an in-memory imitation of the broker's consumer endpoints.
 */
class MiniQueueAutoConfigurationTest {

    private TinyBroker broker;
    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() throws IOException {
        broker = new TinyBroker();
        runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MiniQueueAutoConfiguration.class))
                .withPropertyValues(
                        "miniqueue.client.base-url=" + broker.url(),
                        "miniqueue.client.retry.max-attempts=1",
                        "miniqueue.client.consumer.min-poll-interval=10ms",
                        "miniqueue.client.consumer.max-poll-interval=50ms",
                        "spring.application.name=orders-service");
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    @Test
    void createsAClientBeanFromProperties() {
        runner.run(context -> assertThat(context).hasSingleBean(MiniQueueClient.class));
    }

    @Test
    void anApplicationDefinedClientReplacesTheAutoConfiguredOne() {
        MiniQueueClient custom = MiniQueueClient.builder("http://localhost:9999").build();

        runner.withBean(MiniQueueClient.class, () -> custom)
                .run(context -> assertThat(context.getBean(MiniQueueClient.class)).isSameAs(custom));
    }

    @Test
    void deliversThePayloadToAnAnnotatedMethodAndAcksIt() {
        broker.enqueue("order-1", "order-2");

        runner.withUserConfiguration(PayloadListener.class).run(context -> {
            waitUntil(() -> broker.acked.size() == 2);

            assertThat(context.getBean(PayloadListener.class).received)
                    .containsExactlyInAnyOrder("order-1", "order-2");
            assertThat(broker.queuesPolled).contains("orders-queue");
        });
    }

    @Test
    void passesTheFullMessageAndResolvesPlaceholdersInTheQueueName() {
        broker.enqueue("hello");

        runner.withPropertyValues("app.queue=placeholder-queue")
                .withUserConfiguration(MessageListener.class)
                .run(context -> {
                    MessageListener listener = context.getBean(MessageListener.class);
                    waitUntil(() -> listener.received.size() == 1);

                    assertThat(listener.received.get(0).payload()).isEqualTo("hello");
                    assertThat(broker.queuesPolled).contains("placeholder-queue");
                });
    }

    @Test
    void nacksWhenTheListenerMethodThrows() {
        broker.enqueue("bad");

        runner.withUserConfiguration(FailingListener.class).run(context -> {
            waitUntil(() -> broker.nacked.size() == 1);
            assertThat(broker.acked).isEmpty();
        });
    }

    @Test
    void generatesAConsumerIdThatIsUniqueToThisInstance() {
        runner.withUserConfiguration(PayloadListener.class).run(context -> {
            waitUntil(() -> !broker.consumerIdsPolled.isEmpty());

            assertThat(broker.consumerIdsPolled.get(0))
                    .startsWith("orders-service-")
                    .hasSize("orders-service-".length() + 8);
        });
    }

    @Test
    void anInvalidListenerSignatureFailsStartupWithAClearMessage() {
        runner.withUserConfiguration(TwoParameterListener.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("must take exactly one parameter");
        });
    }

    @Test
    void listenersStopPollingWhenTheContextCloses() throws Exception {
        runner.withUserConfiguration(PayloadListener.class)
                .run(context -> waitUntil(() -> broker.consumeCalls.get() > 0));
        // run() has closed the context by now
        int callsAtClose = broker.consumeCalls.get();
        Thread.sleep(200);

        assertThat(broker.consumeCalls.get()).isEqualTo(callsAtClose);
    }

    @Test
    void autoStartupFalseRegistersListenersWithoutPolling() throws Exception {
        runner.withPropertyValues("miniqueue.client.consumer.auto-startup=false")
                .withUserConfiguration(PayloadListener.class)
                .run(context -> {
                    Thread.sleep(200);
                    assertThat(broker.consumeCalls.get()).isZero();
                });
    }

    // ---------------------------------------------------------------------------------
    // Listener beans used by the tests
    // ---------------------------------------------------------------------------------

    static class PayloadListener {
        final List<String> received = new CopyOnWriteArrayList<>();

        @MiniQueueListener(queue = "orders-queue")
        public void onOrder(String payload) {
            received.add(payload);
        }
    }

    static class MessageListener {
        final List<ReceivedMessage> received = new CopyOnWriteArrayList<>();

        @MiniQueueListener(queue = "${app.queue}")
        public void onMessage(ReceivedMessage message) {
            received.add(message);
        }
    }

    static class FailingListener {
        @MiniQueueListener(queue = "orders-queue")
        public void onOrder(String payload) {
            throw new IllegalStateException("simulated failure for " + payload);
        }
    }

    static class TwoParameterListener {
        @MiniQueueListener(queue = "orders-queue")
        public void onOrder(String payload, String extra) {
        }
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting");
            }
        }
        fail("condition not met within 5 seconds");
    }

    // ---------------------------------------------------------------------------------
    // TinyBroker: in-memory stand-in for the broker's consumer endpoints
    // ---------------------------------------------------------------------------------

    static final class TinyBroker implements AutoCloseable {

        final List<UUID> acked = new CopyOnWriteArrayList<>();
        final List<UUID> nacked = new CopyOnWriteArrayList<>();
        final List<String> queuesPolled = new CopyOnWriteArrayList<>();
        final List<String> consumerIdsPolled = new CopyOnWriteArrayList<>();
        final AtomicInteger consumeCalls = new AtomicInteger();

        private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
        private final ObjectMapper json = new ObjectMapper();
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final HttpServer server;

        TinyBroker() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);

            server.createContext("/consumer/consumeMessage", exchange -> {
                consumeCalls.incrementAndGet();
                recordQuery(exchange.getRequestURI().getRawQuery());
                String payload = pending.poll();
                if (payload == null) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                reply(exchange, json.writeValueAsString(Map.of(
                        "status", 200,
                        "response_body", Map.of(
                                "message_id", UUID.randomUUID().toString(),
                                "queue_name", "orders-queue",
                                "payload", payload,
                                "published_at", "2026-10-19T10:00:00"))));
            });
            server.createContext("/consumer/ackMessage", exchange -> {
                acked.add(messageId(exchange));
                reply(exchange, "{\"status\":200}");
            });
            server.createContext("/consumer/nackMessage", exchange -> {
                nacked.add(messageId(exchange));
                reply(exchange, "{\"status\":200}");
            });
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void enqueue(String... payloads) {
            pending.addAll(List.of(payloads));
        }

        private void recordQuery(String rawQuery) {
            for (String pair : rawQuery.split("&")) {
                String[] kv = pair.split("=", 2);
                String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                if (kv[0].equals("queue_name")) {
                    queuesPolled.add(value);
                } else if (kv[0].equals("consumer_id")) {
                    consumerIdsPolled.add(value);
                }
            }
        }

        private UUID messageId(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            return UUID.fromString(json.readTree(body).get("message_id").asText());
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
}