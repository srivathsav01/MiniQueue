package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsumeResponse {

        private String payload;

        @JsonProperty("message_id")
        private UUID messageId;

        @JsonProperty("published_at")
        private LocalDateTime publishedAt;

        @JsonProperty("queue_name")
        private String queueName;
}
