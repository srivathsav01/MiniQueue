package com.sri.miniqueue.service;

import com.sri.miniqueue.dto.*;
import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.entity.Topic;
import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.to.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

public interface MessageService {

    Topic createTopic(String name) throws CustomException;

    List<TopicResponse> getAllTopics() throws CustomException;

    Queue createQueue(String name, String topicName) throws CustomException;

    List<QueueResponse> getAllQueues() throws CustomException;

    /**
     * Publishes to every queue bound to the topic, in one transaction.
     *
     * With an idempotencyKey, the publish happens at most once per (publisherId, key): a repeat
     * returns the original message ids with duplicate = true and stores nothing.
     */
    PublishResponse publishMessage(String topicName, String payload, UUID publisherId, String idempotencyKey) throws CustomException;

    /** Without an idempotency key: every call stores new messages. Used by tests and internal callers. */
    default PublishResponse publishMessage(String topicName, String payload) throws CustomException {
        return publishMessage(topicName, payload, null, null);
    }

    ConsumeResponse consumeMessage(String queueName, String consumerId) throws CustomException;

    String ackMessage(UUID messageId, String consumerId) throws CustomException;

    String nackMessage(UUID messageId, String consumerId, boolean requeue) throws CustomException;

    List<MessageResponse> getMessagesByQueue(String queueName) throws CustomException;

    String replayMessage(UUID messageId);
}
