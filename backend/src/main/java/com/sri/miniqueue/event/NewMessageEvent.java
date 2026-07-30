package com.sri.miniqueue.event;

import com.sri.miniqueue.dto.ConsumeResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NewMessageEvent {

    private final ConsumeResponse message;

    private final String queueName;
}
