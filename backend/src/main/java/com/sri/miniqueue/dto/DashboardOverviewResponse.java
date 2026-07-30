package com.sri.miniqueue.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewResponse {

    private Long totalTopics;

    private Long totalQueues;

    private Long totalMessages;

    private Long pending;

    private Long unacked;

    private Long acked;

    private Long dead;
}
