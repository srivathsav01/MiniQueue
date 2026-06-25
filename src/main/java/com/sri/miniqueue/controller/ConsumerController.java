package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.AckRequest;
import com.sri.miniqueue.dto.ConsumeResponse;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.CONSUMER)
public class ConsumerController {

    private final MessageService messageService;

    @GetMapping(APIConstants.CONSUME_MESSAGE)
    public Response<ConsumeResponse> consumeResponse(@RequestParam("queue_name") String queueName, @RequestParam("consumer_id") String consumerId){
        return ResponseBuilder.createSuccessData(messageService.consumeMessage(queueName, consumerId));
    }

    @PostMapping(APIConstants.ACK_MESSAGE)
    public Response<String> ackMessage(@RequestBody AckRequest ackRequest){
        return ResponseBuilder.createSuccessData(messageService.ackMessage(ackRequest.getMessageId(),ackRequest.getConsumerId()));
    }
}