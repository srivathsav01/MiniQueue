package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ConsumeRequest {

    @JsonProperty("consumer_id")
    private String consumerId;

    @JsonProperty("queue_name")
    private String queueName;
}
