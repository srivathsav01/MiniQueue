package io.github.srivathsav01.miniqueue.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MiniQueueConsumerTest {

    private StubBroker broker;
    private MiniQueueClient client;

    @BeforeEach
    void setUp() throws Exception {
        broker = new StubBroker();
        client = MiniQueueClient.builder(broker.url())
                .apiKey("mq_test-key-0123456789abcdefghijklmnopqrstuv")
                .retryPolicy(RetryPolicy.none())
                .build();
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    private MiniQueueConsumer.Builder consumer(MessageHandler handler) {
        return MiniQueueConsumer.builder(client, "test-queue", handler);
    }

    @Test
    void acksEveryMessageTheHandlerProcesses() {
        broker.enqueue("a", "b", "c", "d", "e");
        Set<String> handled = ConcurrentHashMap.newKeySet();

        try (MiniQueueConsumer ignored = consumer(message -> handled.add(message.payload()))
                .concurrency(3)
                .build()
                .start()) {
            waitUntil(() -> broker.acked.size() == 5);
        }

        assertEquals(Set.of("a", "b", "c", "d", "e"), handled);
        assertTrue(broker.nacked.isEmpty());
    }

    @Test
    void nacksTheMessageWhenTheHandlerThrows() {
        broker.enqueue("good", "bad");

        try (MiniQueueConsumer ignored = consumer(message -> {
            if (message.payload().equals("bad")) {
                throw new IllegalStateException("simulated processing failure");
            }
        }).build().start()) {
            waitUntil(() -> broker.acked.size() == 1 && broker.nacked.size() == 1);
        }

        assertEquals("good", broker.payloadOf(broker.acked.get(0)));
        assertEquals("bad", broker.payloadOf(broker.nacked.get(0)));
    }

    @Test
    void closeStopsPollingPromptly() throws Exception {
        MiniQueueConsumer consumer = consumer(message -> { }).build().start();
        waitUntil(() -> broker.consumeCalls.get() > 0);

        long started = System.nanoTime();
        consumer.close();
        long closeMillis = (System.nanoTime() - started) / 1_000_000;

        int callsAtClose = broker.consumeCalls.get();
        Thread.sleep(200);

        assertFalse(consumer.isRunning());
        assertEquals(callsAtClose, broker.consumeCalls.get(), "no polling after close()");
        assertTrue(closeMillis < 1_000, "close() wakes sleeping workers instead of waiting them out");
    }

    @Test
    void closeIsIdempotentAndAClosedConsumerCannotRestart() {
        MiniQueueConsumer consumer = consumer(message -> { }).build().start();

        consumer.close();
        consumer.close(); // second close is a no-op

        assertThrows(IllegalStateException.class, consumer::start);
    }

    @Test
    void cannotStartTwice() {
        try (MiniQueueConsumer consumer = consumer(message -> { }).build().start()) {
            assertThrows(IllegalStateException.class, consumer::start);
        }
    }

    /** Polls a condition for up to 5 seconds, because the consumer works on its own threads. */
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
}