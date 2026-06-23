package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.CreateTopicRequest;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.dto.TopicResponse;
import com.sri.miniqueue.entity.Topic;
import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.mapper.TopicMapper;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.TOPIC)
public class TopicController {

    private final MessageService messageService;

    private final TopicMapper topicMapper;


    @PostMapping(APIConstants.CREATE_TOPIC)
    public Response<TopicResponse> createTopic(@RequestBody CreateTopicRequest createTopicRequest) throws CustomException {
        return ResponseBuilder.createSuccessData(topicMapper.toResponse(messageService.createTopic(createTopicRequest.getName())));
    }
}
