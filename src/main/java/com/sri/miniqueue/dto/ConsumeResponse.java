package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class ConsumeResponse {

        private String payload;

        @JsonProperty("message_id")
        private UUID messageId;

        @JsonProperty("published_at")
        private LocalDateTime publishedAt;

        @JsonProperty("queue_name")
        private String queueName;
}
