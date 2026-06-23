package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString
public class QueueResponse {

    @JsonProperty("id")
    private UUID id;

    private String name;

    private String topicName;

}
