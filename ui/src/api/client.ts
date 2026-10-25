import axios from "axios";
import { toast } from "sonner";
import { clearToken, getToken } from "@/auth/token";

export const API_BASE = import.meta.env.VITE_API_BASE_URL || "/";

const apiClient = axios.create({
    baseURL: API_BASE,
    timeout: 10000,
    headers: {
        "Content-Type": "application/json",
    },
});

// Set by AuthProvider. Called when the server says the session is no longer valid.
let onUnauthorized: () => void = () => {};

export function setUnauthorizedHandler(handler: () => void): void {
    onUnauthorized = handler;
}

// Every request carries the session token, the same way an SDK client carries its API key.
apiClient.interceptors.request.use((config) => {
    const token = getToken();
    if (token) {
        config.headers.set("Authorization", `Bearer ${token}`);
    }
    return config;
});

apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error.response?.status;
        const message = error.response?.data?.message || "Something went wrong";
        if (status === 401) {
            // Expired, signed out in another tab, or never signed in: drop the token and let the
            // app return to the sign-in page. No error toast: this is not something the user broke.
            clearToken();
            onUnauthorized();
        } else if (status === 403) {
            toast.error("Not allowed", { description: "Your account has read-only access." });
        } else {
            console.error("API Error:", message);
            toast.error("API Error", { description: message });
        }
        return Promise.reject(new Error(message));
    }
);

export default apiClient;