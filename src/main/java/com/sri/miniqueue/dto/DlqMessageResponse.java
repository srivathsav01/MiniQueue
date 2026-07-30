package com.sri.miniqueue.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DlqMessageResponse {
    private UUID messageId;

    private String queueName;

    private String payload;

    private String consumerId;

    private Integer retryCount;

    private LocalDateTime unackedAt;
}
