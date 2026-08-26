import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import type { BrokerEvent } from "@/types/dashboard";
import { useMonitorWebSocket } from "@/context/MonitorWebSocketContext";

// --- Constants ---

const EVENT_STYLES: Record<
  string,
  { label: string; badge: string; dot: string }
> = {
  PUBLISHED: {
    label: "Published",
    badge: "bg-amber-100 text-amber-700 dark:bg-amber-950/40 dark:text-amber-400 border-amber-200 dark:border-amber-800",
    dot: "bg-amber-400",
  },
  CONSUMED: {
    label: "Consumed",
    badge: "bg-blue-100 text-blue-700 dark:bg-blue-950/40 dark:text-blue-400 border-blue-200 dark:border-blue-800",
    dot: "bg-blue-400",
  },
  ACKED: {
    label: "Acked",
    badge: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800",
    dot: "bg-emerald-400",
  },
  NACKED: {
    label: "Nacked",
    badge: "bg-orange-100 text-orange-700 dark:bg-orange-950/40 dark:text-orange-400 border-orange-200 dark:border-orange-800",
    dot: "bg-orange-400",
  },
  REDELIVERED: {
    label: "Redelivered",
    badge: "bg-purple-100 text-purple-700 dark:bg-purple-950/40 dark:text-purple-400 border-purple-200 dark:border-purple-800",
    dot: "bg-purple-400",
  },
  DEAD: {
    label: "Dead",
    badge: "bg-rose-100 text-rose-700 dark:bg-rose-950/40 dark:text-rose-400 border-rose-200 dark:border-rose-800",
    dot: "bg-rose-500",
  },
  REPLAYED: {
    label: "Replayed",
    badge: "bg-teal-100 text-teal-700 dark:bg-teal-950/40 dark:text-teal-400 border-teal-200 dark:border-teal-800",
    dot: "bg-teal-400",
  },
  CREATED: {
    label: "Created",
    badge: "bg-green-100 text-green-700 dark:bg-green-950/40 dark:text-green-400 border-green-200 dark:border-green-800",
    dot: "bg-green-400",
  }
};

const DEFAULT_STYLE = {
  label: "Event",
  badge: "bg-muted text-muted-foreground border-border",
  dot: "bg-slate-400",
};

function formatTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

// --- Subcomponents ---

function ConnectionStatus({ connected }: { connected: boolean }) {
  return (
    <div className="flex items-center gap-1.5">
      <span
        className={`inline-block h-2 w-2 rounded-full ${
          connected ? "bg-emerald-400 animate-pulse" : "bg-rose-500"
        }`}
      />
      <span className="text-xs text-muted-foreground">
        {connected ? "Connected" : "Disconnected"}
      </span>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center h-48 gap-2 text-muted-foreground">
      <span className="text-2xl">📭</span>
      <p className="text-sm">No activity yet — waiting for broker events.</p>
    </div>
  );
}

function EventRow({ event }: { event: BrokerEvent }) {
  const style = EVENT_STYLES[event.eventType] ?? DEFAULT_STYLE;

  return (
    <div className="flex items-start gap-3 px-4 py-2.5 border-b border-border last:border-0 hover:bg-muted/20 transition-colors">
      {/* Timeline dot */}
      <div className="mt-1.5 flex-shrink-0">
        <span className={`inline-block h-2 w-2 rounded-full ${style.dot}`} />
      </div>

      {/* Badge */}
      <div className="flex-shrink-0 w-28">
        <Badge
          variant="outline"
          className={`text-xs font-medium px-2 py-0.5 ${style.badge}`}
        >
          {style.label}
        </Badge>
      </div>

      {/* Queue name */}
      <span className="flex-shrink-0 w-36 font-mono text-xs text-foreground truncate">
        {event.queueName || "—"}
      </span>

      {/* Detail */}
      <span className="flex-1 text-xs text-muted-foreground truncate">
        {event.detail}
      </span>

      {/* Timestamp */}
      <span className="flex-shrink-0 text-xs text-muted-foreground tabular-nums">
        {formatTime(event.timestamp)}
      </span>
    </div>
  );
}

// --- Main Component ---

export default function LiveActivity() {
  const { events, connected } = useMonitorWebSocket();
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [events]);

  return (
    <section className="m-2 rounded-lg border border-border bg-card p-4 shadow-sm">
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
          Live Activity
        </h2>
        <div className="flex items-center gap-3">
          <span className="text-xs text-muted-foreground tabular-nums">
            {events.length} event{events.length !== 1 ? "s" : ""}
          </span>
          <ConnectionStatus connected={connected} />
        </div>
      </div>

      {/* Feed */}
      <div className="rounded-lg border border-border overflow-hidden">
        {/* Column headers */}
        {events.length > 0 && (
          <div className="flex items-center gap-3 px-4 py-2 bg-muted/40 border-b border-border">
            <div className="w-2 flex-shrink-0" />
            <span className="w-28 flex-shrink-0 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Event
            </span>
            <span className="w-36 flex-shrink-0 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Queue
            </span>
            <span className="flex-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Detail
            </span>
            <span className="flex-shrink-0 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Time
            </span>
          </div>
        )}

        {/* Events or empty state */}
        <div className="max-h-96 overflow-y-auto">
          {events.length === 0 ? (
            <EmptyState />
          ) : (
            events.map((event, index) => (
              <EventRow key={index} event={event} />
            ))
          )}
          <div ref={bottomRef} />
        </div>
      </div>
    </section>
  );
}