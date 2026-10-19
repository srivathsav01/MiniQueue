package io.github.srivathsav01.miniqueue.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sleeper is replaced with a list that records each requested wait, so these tests
 * check the backoff decisions without ever actually sleeping.
 */
class RetryPolicyTest {

    private final List<Duration> sleeps = new ArrayList<>();
    private final RetryPolicy policy = RetryPolicy
            .of(4, Duration.ofMillis(100), Duration.ofMillis(300))
            .withSleeper(sleeps::add);

    @Test
    void retryableFailureIsRetriedUntilItSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = policy.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new MiniQueueException.ServerError("HTTP 503", 503);
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, calls.get());
        assertEquals(2, sleeps.size()); // one wait between each pair of attempts
    }

    @Test
    void givesUpAfterMaxAttemptsAndRethrowsTheLastFailure() {
        AtomicInteger calls = new AtomicInteger();
        MiniQueueException last = new MiniQueueException.ConnectionFailed("refused", null);

        MiniQueueException thrown = assertThrows(MiniQueueException.class, () -> policy.execute(() -> {
            calls.incrementAndGet();
            throw last;
        }));

        assertSame(last, thrown);
        assertEquals(4, calls.get());
        assertEquals(3, sleeps.size());
    }

    @Test
    void nonRetryableFailureIsNeverRetried() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(MiniQueueException.NotFound.class, () -> policy.execute(() -> {
            calls.incrementAndGet();
            throw new MiniQueueException.NotFound("HTTP 404");
        }));

        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void backoffGrowsExponentiallyButNeverPassesTheCap() {
        // Jitter makes each wait random, so check the upper bound over many samples.
        for (int i = 0; i < 1_000; i++) {
            assertTrue(policy.backoffBeforeRetry(1).toMillis() <= 100);  // 100 * 2^0
            assertTrue(policy.backoffBeforeRetry(2).toMillis() <= 200);  // 100 * 2^1
            assertTrue(policy.backoffBeforeRetry(3).toMillis() <= 300);  // 400, capped at 300
            assertTrue(policy.backoffBeforeRetry(30).toMillis() <= 300); // no overflow on huge attempts
        }
    }

    @Test
    void noneMakesExactlyOneAttempt() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(MiniQueueException.ServerError.class, () -> RetryPolicy.none().execute(() -> {
            calls.incrementAndGet();
            throw new MiniQueueException.ServerError("HTTP 500", 500);
        }));

        assertEquals(1, calls.get());
    }
}