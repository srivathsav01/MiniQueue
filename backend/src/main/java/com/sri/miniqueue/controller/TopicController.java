package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.CreateTopicRequest;
import com.sri.miniqueue.dto.PublishMessageRequest;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.dto.TopicResponse;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.mapper.TopicMapper;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.TOPICS)
public class TopicController {

    private final MessageService messageService;

    private final TopicMapper topicMapper;

    // POST /api/v1/topics
    @PostMapping
    public Response<TopicResponse> createTopic(@RequestBody CreateTopicRequest request) {
        return ResponseBuilder.createSuccessData(topicMapper.toResponse(messageService.createTopic(request.getName())));
    }

    // GET /api/v1/topics
    @GetMapping
    public Response<List<TopicResponse>> getAllTopics() {
        return ResponseBuilder.createSuccessData(messageService.getAllTopics());
    }

    // POST /api/v1/topics/{topicName}/messages
    // Publishing adds a message to a topic, so the topic is in the URL, not the body.
    @PostMapping("/{topicName}" + APIConstants.MESSAGES)
    public Response<String> publishMessage(@PathVariable String topicName, @RequestBody PublishMessageRequest request) {
        messageService.publishMessage(topicName, request.getPayload());
        return ResponseBuilder.createSuccessData("Message Successfully Published");
    }
}