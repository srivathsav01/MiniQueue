package io.github.srivathsav01.miniqueue.client;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Decides whether, and how long to wait before, a failed call is tried again.
 *
 * Only failures where {@link MiniQueueException#isRetryable()} is true are retried
 * (5xx responses and connection failures). A 400, 404 or 409 will fail the same way
 * every time, so it is returned to the caller immediately.
 *
 * Waits grow exponentially (200ms, 400ms, 800ms... up to a cap) with "full jitter": each
 * wait is a random time between zero and that value. Without jitter, every client that
 * failed at the same moment, for example during a broker restart, would retry at the
 * same moment too, and hit the recovering broker as one synchronised wave.
 */
public final class RetryPolicy {

    /** Lets tests replace real sleeping with a recorder, so they run instantly. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final Sleeper THREAD_SLEEP = duration -> Thread.sleep(duration.toMillis());

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Sleeper sleeper;

    private RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, Sleeper sleeper) {
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.sleeper = sleeper;
    }

    /** Up to 3 attempts in total, waiting at most 200ms and then 400ms between them (before jitter). */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(200), Duration.ofSeconds(5), THREAD_SLEEP);
    }

    /** Exactly one attempt: every failure goes straight to the caller. */
    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, Duration.ZERO, THREAD_SLEEP);
    }

    /**
     * @param maxAttempts    total attempts including the first one, at least 1
     * @param initialBackoff the wait before the first retry, doubled for each further retry
     * @param maxBackoff     the upper limit for any single wait
     */
    public static RetryPolicy of(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        Objects.requireNonNull(maxBackoff, "maxBackoff");
        if (initialBackoff.isNegative() || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("need 0 <= initialBackoff <= maxBackoff");
        }
        return new RetryPolicy(maxAttempts, initialBackoff, maxBackoff, THREAD_SLEEP);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** Same policy with a different sleeper. Package-private: only tests need it. */
    RetryPolicy withSleeper(Sleeper replacement) {
        return new RetryPolicy(maxAttempts, initialBackoff, maxBackoff, replacement);
    }

    /** Runs the call, retrying retryable failures. The last failure is rethrown unchanged. */
    <T> T execute(Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (MiniQueueException failure) {
                if (!failure.isRetryable() || attempt >= maxAttempts) {
                    throw failure;
                }
                try {
                    sleeper.sleep(backoffBeforeRetry(attempt));
                } catch (InterruptedException interrupted) {
                    // Someone wants this thread to stop: give up now, but keep the flag set.
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(interrupted);
                    throw failure;
                }
            }
        }
    }

    /** The wait after the given failed attempt: random in [0, min(maxBackoff, initial * 2^(attempt-1))]. */
    Duration backoffBeforeRetry(int failedAttempt) {
        int doublings = Math.min(failedAttempt - 1, 20); // 2^20 is already far past any sane cap
        long ceilingMillis = Math.min(maxBackoff.toMillis(), initialBackoff.toMillis() << doublings);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(ceilingMillis + 1));
    }
}