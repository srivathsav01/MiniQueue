package com.sri.miniqueue.dto;

import java.util.UUID;

public interface MessageStatusCount {

    UUID getQueueId();
    String getStatus();
    Long getCount();

}
