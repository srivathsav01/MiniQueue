package io.github.srivathsav01.miniqueue.client;

import java.util.List;
import java.util.UUID;

/**
 * What the broker stored for one publish: one message per queue bound to the topic.
 *
 * @param messageIds one id per bound queue
 * @param duplicate  true when the broker had already accepted this idempotency key, so it
 *                   stored nothing new and returned the original ids. Normal after a retry.
 */
public record PublishResult(List<UUID> messageIds, boolean duplicate) {

    public PublishResult {
        messageIds = List.copyOf(messageIds);
    }
}