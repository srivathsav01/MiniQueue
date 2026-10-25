import type { ApiResponse, SessionInfo } from "@/types/dashboard";
import apiClient, { API_BASE } from "./client";

/**
 * Where the "Sign in with GitHub" button goes. A full-page navigation, not an axios call:
 * the browser itself has to follow the redirects to GitHub and back.
 */
function gitHubSignInUrl(): string {
    const base = new URL(API_BASE, window.location.origin);
    return new URL("api/v1/auth/github/login", base).toString();
}

function exchangeLoginCode(code: string): Promise<SessionInfo> {
    return apiClient.post<ApiResponse<SessionInfo>>("/api/v1/auth/exchange", { code })
        .then((response) => response.data.response_body);
}

function fetchMe(): Promise<SessionInfo> {
    return apiClient.get<ApiResponse<SessionInfo>>("/api/v1/auth/me")
        .then((response) => response.data.response_body);
}

function signOutOnServer(): Promise<unknown> {
    return apiClient.post("/api/v1/auth/logout");
}

function fetchWebSocketTicket(): Promise<string> {
    return apiClient.post<ApiResponse<{ ticket: string; expires_in_seconds: number }>>("/api/v1/auth/ws-ticket")
        .then((response) => response.data.response_body.ticket);
}

export {
    gitHubSignInUrl,
    exchangeLoginCode,
    fetchMe,
    signOutOnServer,
    fetchWebSocketTicket,
};