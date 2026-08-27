package com.sri.miniqueue.mapper;

import com.sri.miniqueue.dto.MessageResponse;
import com.sri.miniqueue.entity.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public MessageResponse toResponse(Message message){
        MessageResponse msgResponse = new MessageResponse();
        msgResponse.setMessageId(message.getId());
        msgResponse.setStatus(String.valueOf(message.getStatus()));
        msgResponse.setPayload(message.getPayload());
        msgResponse.setRetryCount(message.getRetryCount());
        msgResponse.setPublishedAt(message.getPublishedAt());
        msgResponse.setConsumerId(message.getConsumerId());
        msgResponse.setUnackedAt(message.getUnackedAt());
        return msgResponse;
    }
}
