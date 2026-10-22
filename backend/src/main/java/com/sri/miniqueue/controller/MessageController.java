package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.AckRequest;
import com.sri.miniqueue.dto.NackRequest;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Actions on one message that a consumer already holds. */
@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.MESSAGES)
public class MessageController {

    private final MessageService messageService;

    // POST /api/v1/messages/{messageId}/ack
    @PostMapping("/{messageId}/ack")
    public Response<String> ackMessage(@PathVariable UUID messageId, @RequestBody AckRequest request) {
        return ResponseBuilder.createSuccessData(messageService.ackMessage(messageId, request.getConsumerId()));
    }

    // POST /api/v1/messages/{messageId}/nack
    @PostMapping("/{messageId}/nack")
    public Response<String> nackMessage(@PathVariable UUID messageId, @RequestBody NackRequest request) {
        // A missing "requeue" used to unbox null into a NullPointerException (HTTP 500).
        // Default to requeue, the safe choice: the message gets another attempt.
        boolean requeue = request.getRequeue() == null || request.getRequeue();
        return ResponseBuilder.createSuccessData(messageService.nackMessage(messageId, request.getConsumerId(), requeue));
    }
}