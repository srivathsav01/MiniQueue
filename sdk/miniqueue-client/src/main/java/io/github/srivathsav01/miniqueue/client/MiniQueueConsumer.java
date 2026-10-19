package io.github.srivathsav01.miniqueue.client;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a polling loop that feeds messages from a queue to your {@link MessageHandler}:
 * consume, call the handler, then ack on success or nack (requeue) on failure.
 *
 * <pre>{@code
 * MiniQueueClient client = MiniQueueClient.builder("http://localhost:8080").build();
 *
 * MiniQueueConsumer consumer = MiniQueueConsumer
 *         .builder(client, "email-queue", "email-service", message -> sendEmail(message.payload()))
 *         .concurrency(4)
 *         .build()
 *         .start();
 *
 * // ... on application shutdown:
 * consumer.close(); // stops polling, lets in-flight messages finish
 * }</pre>
 *
 * Polling cost: when the queue is empty, each worker waits longer and longer between
 * polls (minPollInterval doubling up to maxPollInterval), so an idle consumer does not
 * hammer the broker. As soon as a message arrives, the wait resets to the minimum.
 *
 * Shutdown: {@link #close()} stops claiming new messages immediately, wakes sleeping
 * workers, and waits up to shutdownTimeout for messages already being handled. A
 * message that is claimed but never acked is not lost: the broker redelivers it after
 * its ack timeout.
 */
public final class MiniQueueConsumer implements AutoCloseable {

    // System.Logger is built into the JDK, so the library adds no logging dependency.
    // Applications that use SLF4J/Logback can route it there with an SLF4J bridge.
    private static final Logger log = System.getLogger(MiniQueueConsumer.class.getName());

    private final MiniQueueClient client;
    private final String queue;
    private final String consumerId;
    private final MessageHandler handler;
    private final int concurrency;
    private final long minPollMillis;
    private final long maxPollMillis;
    private final Duration shutdownTimeout;

    // Counts down once, on close(). Workers sleep by waiting on it, so close() wakes every
    // sleeping worker instantly, without interrupting threads that are running user code.
    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile ExecutorService workers;

    private MiniQueueConsumer(Builder builder) {
        this.client = builder.client;
        this.queue = builder.queue;
        this.consumerId = builder.consumerId;
        this.handler = builder.handler;
        this.concurrency = builder.concurrency;
        this.minPollMillis = builder.minPollInterval.toMillis();
        this.maxPollMillis = builder.maxPollInterval.toMillis();
        this.shutdownTimeout = builder.shutdownTimeout;
    }

    /**
     * @param consumerId identifies this service to the broker; every worker thread uses it,
     *                   which is fine because the broker never hands one message to two claims
     */
    public static Builder builder(MiniQueueClient client, String queue, String consumerId, MessageHandler handler) {
        return new Builder(client, queue, consumerId, handler);
    }

    /** Starts the worker threads. Can be called only once. Returns this, for chaining. */
    public MiniQueueConsumer start() {
        if (stopSignal.getCount() == 0) {
            throw new IllegalStateException("Consumer has been closed and cannot be restarted");
        }
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Consumer is already started");
        }
        AtomicInteger threadNumber = new AtomicInteger();
        workers = Executors.newFixedThreadPool(concurrency, runnable ->
                new Thread(runnable, "miniqueue-" + queue + "-" + threadNumber.incrementAndGet()));
        for (int i = 0; i < concurrency; i++) {
            workers.submit(this::pollLoop);
        }
        log.log(Level.INFO, () -> "Consuming queue '" + queue + "' as '" + consumerId
                + "' with " + concurrency + " worker(s)");
        return this;
    }

    public boolean isRunning() {
        return started.get() && stopSignal.getCount() > 0;
    }

    /** Stops polling and waits for in-flight messages to finish. Safe to call more than once. */
    @Override
    public void close() {
        stopSignal.countDown();
        ExecutorService pool = workers;
        if (pool == null || pool.isShutdown()) {
            return;
        }
        pool.shutdown(); // no new tasks; running loops see the stop signal and exit
        try {
            if (!pool.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.log(Level.WARNING, () -> "Handlers on queue '" + queue + "' did not finish within "
                        + shutdownTimeout + "; interrupting them. Unacked messages will be redelivered.");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.log(Level.INFO, () -> "Stopped consuming queue '" + queue + "'");
    }

    // ---------------------------------------------------------------------------------

    private void pollLoop() {
        long idleWaitMillis = minPollMillis;
        while (!stopping()) {
            try {
                Optional<ReceivedMessage> next = client.consume(queue, consumerId);
                if (next.isEmpty()) {
                    pause(idleWaitMillis);
                    idleWaitMillis = Math.min(idleWaitMillis * 2, maxPollMillis); // back off while idle
                    continue;
                }
                idleWaitMillis = minPollMillis; // messages are flowing again: poll eagerly
                process(next.get());
            } catch (MiniQueueException e) {
                // The client has already retried retryable failures. Either the broker is down
                // for longer than that, or the request is wrong (for example the queue does not
                // exist yet). Keep the worker alive, but wait the maximum before trying again.
                log.log(Level.WARNING, () -> "Polling queue '" + queue + "' failed: " + e.getMessage()
                        + ". Retrying in " + maxPollMillis + "ms.");
                pause(maxPollMillis);
            } catch (RuntimeException e) {
                log.log(Level.ERROR, () -> "Unexpected error while polling queue '" + queue + "'", e);
                pause(maxPollMillis);
            }
        }
    }

    private void process(ReceivedMessage message) {
        try {
            handler.handle(message);
        } catch (Exception handlerFailure) {
            log.log(Level.WARNING, () -> "Handler failed for message " + message.id()
                    + " on queue '" + queue + "'; nacking it for redelivery", handlerFailure);
            settle("nack", message, () -> client.nack(message.id(), consumerId, true));
            return;
        }
        settle("ack", message, () -> client.ack(message.id(), consumerId));
    }

    /** Acks or nacks. A failure here never loses the message: the broker redelivers it. */
    private void settle(String action, ReceivedMessage message, Runnable call) {
        try {
            call.run();
        } catch (MiniQueueException.Conflict e) {
            // Usually: handling took longer than the broker's ack timeout, so the message was
            // already redelivered to someone else. Our ack is rejected (fencing), and the
            // handler may run again elsewhere: that is why handlers must be idempotent.
            log.log(Level.WARNING, () -> "Could not " + action + " message " + message.id()
                    + ": " + e.getMessage() + ". It was probably redelivered after the ack timeout.");
        } catch (MiniQueueException e) {
            log.log(Level.ERROR, () -> "Could not " + action + " message " + message.id()
                    + ": " + e.getMessage() + ". The broker will redeliver it after the ack timeout.");
        }
    }

    private boolean stopping() {
        return stopSignal.getCount() == 0 || Thread.currentThread().isInterrupted();
    }

    /** Sleeps, but wakes up immediately when close() is called. */
    private void pause(long millis) {
        try {
            stopSignal.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // makes stopping() true, so the loop exits
        }
    }

    // ---------------------------------------------------------------------------------

    public static final class Builder {

        private final MiniQueueClient client;
        private final String queue;
        private final String consumerId;
        private final MessageHandler handler;
        private int concurrency = 1;
        private Duration minPollInterval = Duration.ofMillis(100);
        private Duration maxPollInterval = Duration.ofSeconds(5);
        private Duration shutdownTimeout = Duration.ofSeconds(30);

        private Builder(MiniQueueClient client, String queue, String consumerId, MessageHandler handler) {
            this.client = Objects.requireNonNull(client, "client");
            this.queue = requireText(queue, "queue");
            this.consumerId = requireText(consumerId, "consumerId");
            this.handler = Objects.requireNonNull(handler, "handler");
        }

        /** Number of worker threads, each consuming and handling one message at a time. Default 1. */
        public Builder concurrency(int threads) {
            if (threads < 1) {
                throw new IllegalArgumentException("concurrency must be at least 1");
            }
            this.concurrency = threads;
            return this;
        }

        /** Idle polling starts at min and doubles up to max while the queue stays empty. Default 100ms to 5s. */
        public Builder pollInterval(Duration min, Duration max) {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
            if (min.isNegative() || min.isZero() || max.compareTo(min) < 0) {
                throw new IllegalArgumentException("need 0 < min <= max");
            }
            this.minPollInterval = min;
            this.maxPollInterval = max;
            return this;
        }

        /** How long close() waits for in-flight handlers before interrupting them. Default 30s. */
        public Builder shutdownTimeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("shutdownTimeout must not be negative");
            }
            this.shutdownTimeout = timeout;
            return this;
        }

        /** Builds the consumer without starting it. Call {@link MiniQueueConsumer#start()}. */
        public MiniQueueConsumer build() {
            return new MiniQueueConsumer(this);
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}