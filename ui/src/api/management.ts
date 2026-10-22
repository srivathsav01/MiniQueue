import type { ApiResponse, Queue, QueueMessageResponse, Topic } from "@/types/dashboard";
import apiClient from "./client";


function getAllTopics():Promise<Topic[]> {
    return apiClient.get<ApiResponse<Topic[]>>("/api/v1/topics").then((response) => response.data.response_body);
}

function createTopic(topicName: string):Promise<Topic> {
    return apiClient.post<ApiResponse<Topic>>("/api/v1/topics", { name: topicName }).then((response) => response.data.response_body);
}

function getAllQueues():Promise<Queue[]> {
    return apiClient.get<ApiResponse<Queue[]>>("/api/v1/queues").then((response) => response.data.response_body);
}

function createQueue(queueName: string, topicName: string):Promise<Queue> {
    return apiClient.post<ApiResponse<Queue>>("/api/v1/queues", { name: queueName, topic_name:topicName }).then((response) => response.data.response_body);
}

function publishMessage(topicName: string, payload: string):Promise<String> {
    return apiClient.post<ApiResponse<String>>(`/api/v1/topics/${encodeURIComponent(topicName)}/messages`, { payload }).then((response) => response.data.response_body);
}

function getMessagesByQueue(queueName: string): Promise<QueueMessageResponse[]> {
  return apiClient
    .get<ApiResponse<QueueMessageResponse[]>>(`/api/v1/queues/${encodeURIComponent(queueName)}/messages`)
    .then((response) => response.data.response_body);
}

export {
    getAllTopics,
    createTopic,
    getAllQueues,
    createQueue,
    publishMessage,
    getMessagesByQueue
};