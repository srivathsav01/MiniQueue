import {
    createContext,
    useContext,
    useEffect,
    useState,
    type ReactNode,
} from "react";
import { useRefresh } from "./RefreshContext";
import type { BrokerEvent } from "@/types/dashboard";
import { fetchWebSocketTicket } from "@/api/auth";

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
        let ws: WebSocket | null = null;
        let reconnectTimeout: ReturnType<typeof setTimeout> | undefined;
        let stopped = false; // set on unmount (sign-out, leaving the dashboard): no more reconnects

        const scheduleReconnect = () => {
            if (!stopped) {
                reconnectTimeout = setTimeout(connect, 3000);
            }
        };

        const connect = async () => {
            let ticket: string;
            try {
                // A browser cannot send an Authorization header when opening a WebSocket, so trade
                // the session for a one-time ticket (30 seconds, single use) and put that in the URL.
                // Every reconnect needs a fresh ticket, because the last one was used up.
                ticket = await fetchWebSocketTicket();
            } catch {
                // On 401 the app is already returning to sign-in and this provider will unmount.
                scheduleReconnect();
                return;
            }
            if (stopped) {
                return;
            }

            ws = new WebSocket(`${WS_URL}?ticket=${encodeURIComponent(ticket)}`);

            ws.onopen = () => setConnected(true);

            ws.onclose = () => {
                setConnected(false);
                scheduleReconnect();
            };

            ws.onerror = () => {
                setConnected(false);
            };

            ws.onmessage = (event) => {
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
            stopped = true;
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