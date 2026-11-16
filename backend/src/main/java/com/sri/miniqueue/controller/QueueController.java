package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.*;
import com.sri.miniqueue.mapper.QueueMapper;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.security.ClientPrincipal;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.QUEUES)
public class QueueController {

    private final MessageService messageService;

    private final QueueMapper queueMapper;

    // POST /api/v1/queues
    @PostMapping
    public Response<QueueResponse> createQueue(@RequestBody CreateQueueRequest request) {
        return ResponseBuilder.createSuccessData(queueMapper.toResponse(messageService.createQueue(request.getName(), request.getTopicName())));
    }

    // GET /api/v1/queues
    @GetMapping
    public Response<List<QueueResponse>> getAllQueues() {
        return ResponseBuilder.createSuccessData(messageService.getAllQueues());
    }

    // GET /api/v1/queues/{queueName}/messages
    @GetMapping("/{queueName}" + APIConstants.MESSAGES)
    public Response<List<MessageResponse>> getMessagesByQueue(@PathVariable String queueName) {
        return ResponseBuilder.createSuccessData(messageService.getMessagesByQueue(queueName));
    }

    // POST /api/v1/queues/{queueName}/consume   (no body)
    // The consumer is whoever owns the API key, not whatever the request claims.
    @PostMapping("/{queueName}/consume")
    public Response<ConsumeResponse> consumeMessage(@PathVariable String queueName,
                                                    @AuthenticationPrincipal ClientPrincipal client,
                                                    @RequestHeader(value = APIConstants.INSTANCE_HEADER, required = false) String instance) {
        return ResponseBuilder.createSuccessData(messageService.consumeMessage(queueName, client.consumerId(instance)));
    }

    // PATCH /api/v1/queues/{queueName}   { "lease_seconds": 15 }
    @PatchMapping("/{queueName}")
    public Response<QueueResponse> updateQueue(@PathVariable String queueName, @RequestBody UpdateQueueRequest request) {
        return ResponseBuilder.createSuccessData(queueMapper.toResponse(
                messageService.updateQueueLease(queueName, request.getLeaseSeconds())));
    }
}