package io.github.srivathsav01.miniqueue.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method on a Spring bean as the handler for a MiniQueue queue.
 *
 * <pre>{@code
 * @Component
 * class EmailListener {
 *
 *     @MiniQueueListener(queue = "email-queue")
 *     public void onOrderPlaced(String payload) {
 *         emailService.sendConfirmation(payload);
 *     }
 * }
 * }</pre>
 *
 * The method must be non-private and take exactly one parameter: a {@code String} to
 * receive just the payload, or a {@code ReceivedMessage} to also get the id, queue name
 * and publish time.
 *
 * Returning normally acks the message. Throwing nacks it with requeue, and the broker
 * moves it to the dead-letter queue after its maximum number of retries. Delivery is
 * at-least-once, so the method should be idempotent.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MiniQueueListener {

    /** The queue to consume from. Supports placeholders, e.g. {@code "${app.queues.email}"}. */
    String queue();

    /**
     * Overrides the consumer id for this listener. Leave empty to use
     * {@code miniqueue.client.consumer.consumer-id}, or a generated id that is unique to this
     * application instance. Supports placeholders.
     */
    String consumerId() default "";

    /** Worker threads for this listener. 0 (the default) uses {@code miniqueue.client.consumer.concurrency}. */
    int concurrency() default 0;
}