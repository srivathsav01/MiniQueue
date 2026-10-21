package com.sri.miniqueue;

import com.sri.miniqueue.entity.Topic;
import com.sri.miniqueue.exception.ConflictException;
import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.exception.PayloadTooLargeException;
import com.sri.miniqueue.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

// A small payload limit, so the size test does not need 256 KB strings.
@SpringBootTest(properties = "miniqueue.limits.max-payload-bytes=1024")
@Testcontainers
class TopicQueueIntegrityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MessageService messageService;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void concurrentCreatesOfTheSameTopicProduceExactlyOneTopic() throws Exception {
        String topic = "race-" + suffix();
        int threads = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                startGate.await();
                try {
                    messageService.createTopic(topic);
                    return "created";
                } catch (ConflictException e) {
                    return "conflict";
                }
                // Any other exception fails the test through Future.get below.
            }));
        }
        startGate.countDown();

        int created = 0;
        int conflicts = 0;
        for (Future<String> result : results) {
            if (result.get(30, TimeUnit.SECONDS).equals("created")) created++;
            else conflicts++;
        }
        pool.shutdown();

        assertEquals(1, created, "exactly one request may create the topic");
        assertEquals(threads - 1, conflicts, "every other request must get a 409");
    }

    @Test
    void duplicateQueueNameIsAConflictEvenWithDifferentCase() {
        String s = suffix();
        messageService.createTopic("t-" + s);
        messageService.createQueue("q-" + s, "t-" + s);

        assertThrows(ConflictException.class, () -> messageService.createQueue("Q-" + s, "t-" + s));
    }

    @Test
    void namesAreNormalizedAndValidated() {
        String s = suffix();
        Topic topic = messageService.createTopic("  Orders.Placed-" + s + " ");
        assertEquals("orders.placed-" + s, topic.getName());

        String[] invalid = {null, "", "   ", "has space", "-leading-dash", "semi;colon", "a".repeat(101)};
        for (String bad : invalid) {
            // Exactly CustomException (400), not a subclass such as Conflict or NotFound.
            assertThrowsExactly(CustomException.class, () -> messageService.createTopic(bad), "should reject: " + bad);
        }
    }

    @Test
    void payloadLimitCountsBytesNotCharacters() {
        String s = suffix();
        messageService.createTopic("t-" + s);
        messageService.createQueue("q-" + s, "t-" + s);

        // \u00e9 is é: 1 character, 2 bytes in UTF-8.
        messageService.publishMessage("t-" + s, "\u00e9".repeat(512)); // 1024 bytes: allowed
        assertThrows(PayloadTooLargeException.class,
                () -> messageService.publishMessage("t-" + s, "\u00e9".repeat(513))); // 1026 bytes
        assertThrowsExactly(CustomException.class, () -> messageService.publishMessage("t-" + s, null));
    }
}