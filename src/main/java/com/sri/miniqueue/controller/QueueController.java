package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.CreateQueueRequest;
import com.sri.miniqueue.dto.QueueResponse;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.mapper.QueueMapper;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping(APIConstants.QUEUE)
public class QueueController {

    private final MessageService messageService;

    private final QueueMapper queueMapper;


    @PostMapping(APIConstants.CREATE_QUEUE)
    public Response<QueueResponse> createQueue(@RequestBody CreateQueueRequest createQueueRequest) throws CustomException{
        return ResponseBuilder.createSuccessData(queueMapper.toResponse(messageService.createQueue(createQueueRequest.getName(),createQueueRequest.getTopicName())));
    }

}
