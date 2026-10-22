package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.DashboardOverviewResponse;
import com.sri.miniqueue.dto.DlqMessageResponse;
import com.sri.miniqueue.dto.QueueStatsResponse;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.service.DashboardService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.DASHBOARD)
public class DashboardController {

    private final DashboardService dashboardService;

    // GET /api/v1/dashboard/overview
    @GetMapping("/overview")
    public Response<DashboardOverviewResponse> getOverview(){
        return ResponseBuilder.createSuccessData(dashboardService.getOverview());
    }

    // GET /api/v1/dashboard/queues
    @GetMapping(APIConstants.QUEUES)
    public Response<List<QueueStatsResponse>> getQueueStats(){
        return ResponseBuilder.createSuccessData(dashboardService.getQueueStats());
    }

    // GET /api/v1/dashboard/dlq
    @GetMapping("/dlq")
    public Response<List<DlqMessageResponse>> getDeadMessages(){
        return ResponseBuilder.createSuccessData(dashboardService.getDeadLetterMessages());
    }

    // GET /api/v1/dashboard/dlq/{messageId}/replay
    @PostMapping("/dlq/{messageId}/replay")
    public Response<String> replayMessage(@PathVariable UUID messageId){
        return ResponseBuilder.createSuccessData(dashboardService.replayMessage(messageId));
    }
}