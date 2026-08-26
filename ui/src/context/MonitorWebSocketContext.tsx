import {
    createContext,
    useContext,
    useEffect,
    useState,
    type ReactNode,
} from "react";
import { useRefresh } from "./RefreshContext";
import type { BrokerEvent } from "@/types/dashboard";


interface MonitorWebSocketContextValue {
    events: BrokerEvent[];
    connected: boolean;
}

// --- Context ---

const MonitorWebSocketContext =
    createContext<MonitorWebSocketContextValue | null>(null);

// --- Provider ---
const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const wsBase = import.meta.env.VITE_WS_BASE_URL || window.location.host;
const WS_URL = `${protocol}//${wsBase}/ws/monitor`;

export function MonitorWebSocketProvider({ children }: { children: ReactNode }) {
    const [events, setEvents] = useState<BrokerEvent[]>([]);
    const [connected, setConnected] = useState(false);
    const { triggerRefresh } = useRefresh();

    useEffect(() => {
        let ws: WebSocket;
        let reconnectTimeout: ReturnType<typeof setTimeout>;

        const connect = () => {
            ws = new WebSocket(WS_URL);

            ws.onopen = () => setConnected(true);

            ws.onclose = () => {
                setConnected(false);
                // Auto-reconnect after 3 seconds
                reconnectTimeout = setTimeout(connect, 3000);
            };

            ws.onerror = () => {
                setConnected(false);
            };

            ws.onmessage = (event) => {
                console.log("Received event:", event.data);
                try {
                    const data: BrokerEvent = JSON.parse(event.data);
                    setEvents((prev) => [...prev.slice(-49), data]);
                    triggerRefresh();
                } catch {
                    // malformed event — ignore
                }
            };
        };

        connect();

        return () => {
            clearTimeout(reconnectTimeout);
            ws?.close();
        };
    }, [triggerRefresh]);

    return (
        <MonitorWebSocketContext.Provider value={{ events, connected }}>
            {children}
        </MonitorWebSocketContext.Provider>
    );
}

// --- Hook ---

export function useMonitorWebSocket(): MonitorWebSocketContextValue {
    const ctx = useContext(MonitorWebSocketContext);
    if (!ctx) {
        throw new Error(
            "useMonitorWebSocket must be used inside <MonitorWebSocketProvider>"
        );
    }
    return ctx;
}