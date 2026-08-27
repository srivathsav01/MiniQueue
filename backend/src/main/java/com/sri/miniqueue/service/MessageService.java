package com.sri.miniqueue.service;

import com.sri.miniqueue.dto.ConsumeResponse;
import com.sri.miniqueue.dto.MessageResponse;
import com.sri.miniqueue.dto.QueueResponse;
import com.sri.miniqueue.dto.TopicResponse;
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

    void publishMessage(String topicName, String payload) throws CustomException;

    ConsumeResponse consumeMessage(String queueName, String consumerId) throws CustomException;

    String ackMessage(UUID messageId, String consumerId) throws CustomException;

    String nackMessage(UUID messageId, String consumerId, boolean requeue) throws CustomException;

    List<MessageResponse> getMessagesByQueue(String queueName) throws CustomException;
}
