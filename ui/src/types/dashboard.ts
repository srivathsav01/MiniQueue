export interface DashboardOverview {
    totalTopics: number;
    totalQueues: number;
    totalMessages: number;
    pending: number;
    unacked: number;
    acked: number;
    dead: number;
}

export interface QueueStats {
    queueName: string;
    topicName: string;
    pending: number;
    unacked: number;
    acked: number;
    dead: number;
}

export interface DlqMessage {
    messageId: string;
    queueName: string;
    payload: string;
    consumerId: string;
    retryCount: number;
    unackedAt: String;
}

export interface ApiResponse<T> {
    status: number;
    message: string;
    response_body: T;
    errors?: string[];
}