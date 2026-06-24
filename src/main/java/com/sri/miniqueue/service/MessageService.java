package com.sri.miniqueue.service;

import com.sri.miniqueue.dto.ConsumeResponse;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.entity.Topic;
import com.sri.miniqueue.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

public interface MessageService {

    Topic createTopic(String name) throws CustomException;

    Queue createQueue(String name, String topicName) throws CustomException;

    void publishMessage(String topicName, String payload) throws CustomException;

    ConsumeResponse consumeMessage(String queueName, String consumerId) throws CustomException;
}
