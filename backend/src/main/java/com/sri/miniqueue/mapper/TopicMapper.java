package com.sri.miniqueue.mapper;

import com.sri.miniqueue.dto.TopicResponse;
import com.sri.miniqueue.entity.Topic;
import org.springframework.stereotype.Component;

@Component
public class TopicMapper {

    public TopicResponse toResponse(Topic topic){
        TopicResponse topicResponse = new TopicResponse();
        topicResponse.setId(topic.getId());
        topicResponse.setName(topic.getName());
        return topicResponse;
    }
}
