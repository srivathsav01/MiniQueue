package io.github.srivathsav01.miniqueue.client;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A message claimed from a queue. Until it is acked or nacked, the broker holds it in
 * the UNACKED state for this consumer; if neither happens before the broker's ack
 * timeout, it is redelivered to someone else.
 *
 * @param id          use this to ack or nack the message
 * @param queueName   the queue it was consumed from
 * @param payload     the message body exactly as it was published
 * @param publishedAt when the broker stored it
 */
public record ReceivedMessage(UUID id, String queueName, String payload, LocalDateTime publishedAt) {
}