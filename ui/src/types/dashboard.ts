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

export interface BrokerEvent {
  eventType: string;
  queueName: string;
  detail: string;
  timestamp: string;
}

export interface Topic {
    id: string;
    name: string;
}

export interface Queue {
    id: string;
    name: string;
    topicName: string;
}

export interface QueueMessageResponse {
  messageId: string;
  payload: string;
  status: string;
  consumerId: string | null;
  retryCount: number;
  publishedAt: string;
  unackedAt: string | null;
}

/** Who is signed in. token and expires_at only come back from the sign-in exchange. */
export interface SessionInfo {
    token?: string;
    username: string;
    scopes: string[];
    expires_at?: string;
}

/** A registered application (producer or consumer) and its API key metadata. */
export interface BrokerClient {
    id: string;
    name: string;
    scopes: string[];
    key_prefix: string;
    /** Only present right after create or rotate. */
    api_key?: string;
    created_at: string;
    last_used_at?: string;
}

/** What one publish stored: one message per bound queue. duplicate = an earlier send with the same key already did. */
export interface PublishResult {
    message_ids: string[];
    duplicate: boolean;
}