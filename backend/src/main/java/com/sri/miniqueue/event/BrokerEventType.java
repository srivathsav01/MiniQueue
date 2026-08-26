package com.sri.miniqueue.event;

public enum BrokerEventType {
    PUBLISHED,
    CONSUMED,
    ACKED,
    NACKED,
    REDELIVERED,
    DEAD,
    REPLAYED,
    CREATED
}
