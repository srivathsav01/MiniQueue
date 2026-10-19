package io.github.srivathsav01.miniqueue.client;

/**
 * Your code that processes one message, used by {@link MiniQueueConsumer}.
 *
 * Return normally and the message is acked. Throw any exception and it is nacked with
 * requeue, so the broker delivers it again (and moves it to the dead-letter queue after
 * its maximum number of retries).
 *
 * Delivery is at-least-once, so the same message can occasionally arrive twice, for
 * example if an ack is lost. Handlers should be idempotent: processing a message twice
 * must have the same effect as processing it once.
 */
@FunctionalInterface
public interface MessageHandler {

    void handle(ReceivedMessage message) throws Exception;
}