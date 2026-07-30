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

    @GetMapping(APIConstants.OVERVIEW)
    public Response<DashboardOverviewResponse> getOverview(){
        return ResponseBuilder.createSuccessData(dashboardService.getOverview());
    }

    @GetMapping(APIConstants.QUEUES)
    public Response<List<QueueStatsResponse>> getQueueStats(){
        return ResponseBuilder.createSuccessData(dashboardService.getQueueStats());
    }

    @GetMapping(APIConstants.DLQ)
    public Response<List<DlqMessageResponse>> getDeadMessages(){
        return ResponseBuilder.createSuccessData(dashboardService.getDeadLetterMessages());
    }

    @PostMapping(APIConstants.DLQ+"/{messageId}"+APIConstants.REPLAY)
    public Response<String> replayMessage(@PathVariable UUID messageId){
        return ResponseBuilder.createSuccessData(dashboardService.replayMessage(messageId));
    }
}