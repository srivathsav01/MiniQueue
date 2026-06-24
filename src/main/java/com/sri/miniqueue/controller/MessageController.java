package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.PublishMessageRequest;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.MESSAGE)
public class MessageController {

    private final MessageService messageService;

    @PostMapping(APIConstants.PUBLISH_MESSAGE)
    public Response<String> publishMessage(@RequestBody PublishMessageRequest publishMessageRequest) {
        messageService.publishMessage(publishMessageRequest.getTopicName(),publishMessageRequest.getPayload());
        return ResponseBuilder.createSuccessData("Message Successfully Published");
    }
}
