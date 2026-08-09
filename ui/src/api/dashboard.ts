import type { ApiResponse, DashboardOverview, DlqMessage, QueueStats } from "../types/dashboard";
import apiClient from "./client";

function getOverview(): Promise<DashboardOverview> {
    return apiClient.get<ApiResponse<DashboardOverview>>("/dashboard/overview").then((response) => response.data.response_body);
}

function getQueueStats(): Promise<QueueStats[]> {
    return apiClient.get<ApiResponse<QueueStats[]>>("/dashboard/queues").then((response) => response.data.response_body);
}

function getDlqMessages(): Promise<DlqMessage[]> {
    return apiClient.get<ApiResponse<DlqMessage[]>>("/dashboard/dlq").then((response) => response.data.response_body);
}

function replayMessage(messageId: string): Promise<String> {
    return apiClient.post<ApiResponse<String>>(`/dashboard/dlq/${messageId}/replay`).then((response) => response.data.response_body);
}

export {
    getOverview,
    getQueueStats,
    getDlqMessages,
    replayMessage,
};