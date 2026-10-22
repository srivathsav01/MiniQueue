package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.ConsumeRequest;
import com.sri.miniqueue.dto.ConsumeResponse;
import com.sri.miniqueue.dto.CreateQueueRequest;
import com.sri.miniqueue.dto.MessageResponse;
import com.sri.miniqueue.dto.QueueResponse;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.mapper.QueueMapper;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
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

    // POST /api/v1/queues/{queueName}/consume
    // POST, not GET: consuming claims a message (PENDING -> UNACKED). GET must be safe to
    // repeat, and browsers, proxies and link prefetchers are allowed to resend a GET.
    // 200 with the message, or 204 when nothing is claimable right now.
    @PostMapping("/{queueName}/consume")
    public Response<ConsumeResponse> consumeMessage(@PathVariable String queueName, @RequestBody ConsumeRequest request) {
        return ResponseBuilder.createSuccessData(messageService.consumeMessage(queueName, request.getConsumerId()));
    }
}