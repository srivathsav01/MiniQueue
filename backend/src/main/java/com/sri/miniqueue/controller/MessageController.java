package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.NackRequest;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.security.ClientPrincipal;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Actions on one message that a consumer already holds. */
@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.MESSAGES)
public class MessageController {

    private final MessageService messageService;

    // POST /api/v1/messages/{messageId}/ack   (no body)
    @PostMapping("/{messageId}/ack")
    public Response<String> ackMessage(@PathVariable UUID messageId,
                                       @AuthenticationPrincipal ClientPrincipal client,
                                       @RequestHeader(value = APIConstants.INSTANCE_HEADER, required = false) String instance) {
        return ResponseBuilder.createSuccessData(messageService.ackMessage(messageId, client.consumerId(instance)));
    }

    // POST /api/v1/messages/{messageId}/nack   body optional: { "requeue": false }
    @PostMapping("/{messageId}/nack")
    public Response<String> nackMessage(@PathVariable UUID messageId,
                                        @AuthenticationPrincipal ClientPrincipal client,
                                        @RequestHeader(value = APIConstants.INSTANCE_HEADER, required = false) String instance,
                                        @RequestBody(required = false) NackRequest request) {
        // No body or no "requeue" means requeue: the safe default gives the message another attempt.
        boolean requeue = request == null || request.getRequeue() == null || request.getRequeue();
        return ResponseBuilder.createSuccessData(messageService.nackMessage(messageId, client.consumerId(instance), requeue));
    }
}