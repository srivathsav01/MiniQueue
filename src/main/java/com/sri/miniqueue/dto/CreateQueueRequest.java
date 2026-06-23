package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateQueueRequest {

    private String name;

    @JsonProperty("topic_name")
    private String topicName;
}