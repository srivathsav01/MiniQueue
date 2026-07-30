package com.sri.miniqueue.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueueStatsResponse {

    private String queueName;

    private String topicName;

    private Long pending;

    private Long unacked;

    private Long acked;

    private Long dead;
}
