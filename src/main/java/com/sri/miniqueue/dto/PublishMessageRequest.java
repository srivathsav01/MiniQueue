package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublishMessageRequest {

    @JsonProperty("topic_name")
    private String topicName;

    private String payload;
}
