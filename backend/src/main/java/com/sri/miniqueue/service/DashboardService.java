package com.sri.miniqueue.service;

import com.sri.miniqueue.dto.DashboardOverviewResponse;
import com.sri.miniqueue.dto.DlqMessageResponse;
import com.sri.miniqueue.dto.QueueStatsResponse;

import java.util.List;
import java.util.UUID;

public interface DashboardService {

    DashboardOverviewResponse getOverview();

    List<QueueStatsResponse> getQueueStats();

    List<DlqMessageResponse> getDeadLetterMessages();

    String replayMessage(UUID messageId);
}
