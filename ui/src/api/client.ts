import axios from "axios";
import { toast } from "sonner";

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || "/",
    timeout: 10000,
    headers: {
        "Content-Type": "application/json",
    },
});

apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        const message = error.response?.data?.message || "Something went wrong";
        console.error("API Error:", message);
        toast.error("API Error", {
            description: message,
        });
        return Promise.reject(new Error(message));
    }
);

export default apiClient;