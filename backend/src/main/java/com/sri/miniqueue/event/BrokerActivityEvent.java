package com.sri.miniqueue.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class BrokerActivityEvent {

    private final BrokerEventType eventType;

    private final String queueName;

    private final String detail;

    private final LocalDateTime timestamp;
}
