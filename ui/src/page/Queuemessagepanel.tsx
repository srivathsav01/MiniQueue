import { useEffect, useState } from "react";
import { Copy, Check } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { QueueMessageResponse } from "@/types/dashboard";
import { getMessagesByQueue } from "@/api/management";

const STATUS_STYLES: Record<string, string> = {
  PENDING:
    "bg-amber-100 text-amber-700 dark:bg-amber-950/40 dark:text-amber-400 border-amber-200 dark:border-amber-800",
  UNACKED:
    "bg-orange-100 text-orange-700 dark:bg-orange-950/40 dark:text-orange-400 border-orange-200 dark:border-orange-800",
  ACKED:
    "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800",
  DEAD: "bg-rose-100 text-rose-700 dark:bg-rose-950/40 dark:text-rose-400 border-rose-200 dark:border-rose-800",
};

function formatDate(value: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(value).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  };

  return (
    <button
      onClick={handleCopy}
      title={`Copy ${label}`}
      className="flex items-center gap-0.5 text-[10px] text-muted-foreground hover:text-foreground transition-colors"
    >
      {copied ? (
        <Check className="size-3 text-emerald-500" />
      ) : (
        <Copy className="size-3" />
      )}
      {copied ? "Copied" : label}
    </button>
  );
}

function MessageCard({ msg }: { msg: QueueMessageResponse }) {
  const statusStyle =
    STATUS_STYLES[msg.status] ?? "bg-muted text-muted-foreground border-border";

  return (
    <div className="flex-shrink-0 w-56 rounded-lg border border-border bg-card p-3 space-y-2 hover:bg-muted/20 transition-colors">
      {/* Status + ID */}
      <div className="flex items-center justify-between gap-1">
        <Badge variant="outline" className={`text-[10px] px-1.5 py-0 ${statusStyle}`}>
          {msg.status}
        </Badge>
        <span className="text-[10px] font-mono text-muted-foreground truncate">
          {msg.messageId.substring(0, 8)}...
        </span>
      </div>

      {/* Payload preview */}
      <div className="rounded bg-muted/40 border border-border px-2 py-1.5">
        <p className="text-[10px] font-mono text-foreground line-clamp-2 break-all">
          {msg.payload}
        </p>
      </div>

      {/* Copy buttons */}
      <div className="flex items-center gap-3">
        <CopyButton value={msg.messageId} label="ID" />
        <CopyButton value={msg.payload} label="Payload" />
      </div>

      {/* Metadata */}
      <div className="grid grid-cols-2 gap-x-2 gap-y-1 text-[10px]">
        <div>
          <p className="text-muted-foreground">Published</p>
          <p className="font-medium tabular-nums">{formatDate(msg.publishedAt)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Unacked</p>
          <p className="font-medium tabular-nums">{formatDate(msg.unackedAt)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Consumer</p>
          <p className="font-mono font-medium truncate">{msg.consumerId || "—"}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Retries</p>
          <p
            className={`font-medium tabular-nums ${
              msg.retryCount > 0 ? "text-orange-600 dark:text-orange-400" : ""
            }`}
          >
            {msg.retryCount}
          </p>
        </div>
      </div>
    </div>
  );
}

function SkeletonCards() {
  return (
    <div className="flex gap-3 overflow-x-auto pb-2">
      {Array.from({ length: 4 }).map((_, i) => (
        <div
          key={i}
          className="flex-shrink-0 w-56 rounded-lg border border-border p-3 space-y-2"
        >
          <div className="flex items-center justify-between">
            <Skeleton className="h-4 w-14" />
            <Skeleton className="h-3 w-16" />
          </div>
          <Skeleton className="h-8 w-full" />
          <div className="grid grid-cols-2 gap-2">
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-6 w-full" />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function QueueMessagePanel({ queueName }: { queueName: string }) {
  const [messages, setMessages] = useState<QueueMessageResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    getMessagesByQueue(queueName)
      .then(setMessages)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, [queueName]);

  if (error) {
    return (
      <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 dark:bg-rose-950/30 dark:border-rose-800 px-4 py-3 text-xs text-rose-700 dark:text-rose-400">
        Failed to load messages — {error}
      </div>
    );
  }

  return (
    <div className="mt-3">
      {loading ? (
        <SkeletonCards />
      ) : messages.length === 0 ? (
        <div className="flex items-center justify-center py-8 gap-2 text-muted-foreground">
          <span className="text-lg">📭</span>
          <p className="text-xs">No messages in this queue.</p>
        </div>
      ) : (
        <>
          <p className="text-[10px] text-muted-foreground mb-2">
            {messages.length} message{messages.length !== 1 ? "s" : ""}
          </p>
          <div className="flex gap-3 overflow-x-auto pb-2">
            {messages.map((msg) => (
              <MessageCard key={msg.messageId} msg={msg} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}