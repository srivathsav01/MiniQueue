package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AckRequest {

    @JsonProperty("consumer_id")
    private String consumerId;

    @JsonProperty("message_id")
    private UUID messageId;
}
