package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.*;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.mapper.TopicMapper;
import com.sri.miniqueue.security.ClientPrincipal;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    // POST /api/v1/topics/{topicName}/messages   optional header: Idempotency-Key
    @PostMapping("/{topicName}" + APIConstants.MESSAGES)
    public Response<PublishResponse> publishMessage(@PathVariable String topicName,
                                                    @RequestBody PublishMessageRequest request,
                                                    @AuthenticationPrincipal ClientPrincipal publisher,
                                                    @RequestHeader(value = APIConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        return ResponseBuilder.createSuccessData(
                messageService.publishMessage(topicName, request.getPayload(), publisher.id(), idempotencyKey));
    }
}