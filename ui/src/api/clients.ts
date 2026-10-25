import type { ApiResponse, BrokerClient } from "@/types/dashboard";
import apiClient from "./client";

function listClients(): Promise<BrokerClient[]> {
    return apiClient.get<ApiResponse<BrokerClient[]>>("/api/v1/clients")
        .then((response) => response.data.response_body);
}

/** The response's api_key is shown this once; the server keeps only its hash. */
function createClient(name: string, scopes: string[]): Promise<BrokerClient> {
    return apiClient.post<ApiResponse<BrokerClient>>("/api/v1/clients", { name, scopes })
        .then((response) => response.data.response_body);
}

function rotateClientKey(name: string): Promise<BrokerClient> {
    return apiClient.post<ApiResponse<BrokerClient>>(`/api/v1/clients/${encodeURIComponent(name)}/rotate`)
        .then((response) => response.data.response_body);
}

function revokeClient(name: string): Promise<string> {
    return apiClient.delete<ApiResponse<string>>(`/api/v1/clients/${encodeURIComponent(name)}`)
        .then((response) => response.data.response_body);
}

export {
    listClients,
    createClient,
    rotateClientKey,
    revokeClient,
};