package com.sri.miniqueue;

import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.exception.NoMessageAvailableException;
import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.to.MessageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
public class ConcurrencyIntegrationTest {

private static final int MESSAGES = 100;
private static final int CONSUMERS = 10;

@Container
@ServiceConnection
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

@Autowired
private MessageService messageService;

@Autowired
private MessageRepository messageRepository;

@Test
void concurrentConsumersNeverReceiveTheSameMessage() throws Exception {
    // Unique names, so this test never collides with data from other tests that share
    // the same container.
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String topic = "topic-" + suffix;
    String queue = "queue-" + suffix;

    // Arrange: one topic, one queue bound to it, and MESSAGES pending messages.
    messageService.createTopic(topic);
    messageService.createQueue(queue, topic);
    for (int i = 0; i < MESSAGES; i++) {
        messageService.publishMessage(topic, "payload-" + i);
    }

    // Every delivered message id lands here. A thread-safe collection that keeps
    // duplicates, so a double delivery shows up as an extra entry.
    ConcurrentLinkedQueue<UUID> delivered = new ConcurrentLinkedQueue<>();

    // The start gate holds every consumer back until all of them are ready, then
    // releases them at the same instant. Without it, thread 1 could drain half the
    // queue before thread 10 even starts, and there would be no real contention.
    CountDownLatch startGate = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(CONSUMERS);
    List<Future<Integer>> consumers = new ArrayList<>();

    for (int c = 0; c < CONSUMERS; c++) {
        String consumerId = "consumer-" + c;
        consumers.add(pool.submit(() -> {
            startGate.await();
            int received = 0;
            while (true) {
                try {
                    delivered.add(messageService.consumeMessage(queue, consumerId).getMessageId());
                    received++;
                } catch (NoMessageAvailableException e) {
                    return received;
                }
            }
        }));
    }

    // Act: release all consumers at once, then wait for every one of them to finish.
    startGate.countDown();
    for (Future<Integer> consumer : consumers) {
        // get() rethrows anything a consumer thread threw, so the test fails loudly
        // instead of hiding a crash inside the thread pool.
        consumer.get(60, TimeUnit.SECONDS);
    }
    pool.shutdown();

    // Assert 1: no duplicates. With the old look-then-act code, two consumers could
    // receive the same message, so 'delivered' would hold more ids than distinct ids.
    Set<UUID> distinct = new HashSet<>(delivered);
    assertEquals(delivered.size(), distinct.size(),
            (delivered.size() - distinct.size()) + " message(s) were delivered more than once");

    // Assert 2: nothing lost. Every published message was delivered.
    assertEquals(MESSAGES, distinct.size(), "every published message is delivered");

    // Assert 3: the database agrees. All messages are now UNACKED, none left PENDING.
    assertEquals(0L, messageRepository.countByQueueNameAndStatus(queue, MessageStatus.PENDING));
    assertEquals((long) MESSAGES, messageRepository.countByQueueNameAndStatus(queue, MessageStatus.UNACKED));
}
}
