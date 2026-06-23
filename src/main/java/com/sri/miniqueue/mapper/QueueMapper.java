package com.sri.miniqueue.mapper;

import com.sri.miniqueue.dto.QueueResponse;
import com.sri.miniqueue.dto.TopicResponse;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.entity.Topic;
import org.springframework.stereotype.Component;

@Component
public class QueueMapper {

    public QueueResponse toResponse(Queue queue){
        QueueResponse queueResponse = new QueueResponse();
        queueResponse.setId(queue.getId());
        queueResponse.setName(queue.getName());
        queueResponse.setTopicName(queue.getTopic().getName());
        return queueResponse;
    }
}
