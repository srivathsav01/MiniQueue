import { useEffect, useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import type { QueueStats } from "@/types/dashboard";
import { getQueueStats } from "@/api/dashboard";
import { useRefresh } from "@/context/RefreshContext";
import QueueMessagePanel from "@/page/Queuemessagepanel";
import ErrorDisplay from "@/page/ErrorPage";

function StatusCell({
  value,
  color,
  bold,
}: {
  value: number;
  color: string;
  bold?: boolean;
}) {
  return (
    <TableCell className={`tabular-nums ${color} ${bold ? "font-bold" : ""}`}>
      {value ? value.toLocaleString() : "-"}
    </TableCell>
  );
}

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <TableRow key={i}>
          {Array.from({ length: 6 }).map((_, j) => (
            <TableCell key={j}>
              <Skeleton className="h-4 w-16" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  );
}

function EmptyState() {
  return (
    <TableRow>
      <TableCell colSpan={6} className="text-center py-12 text-muted-foreground text-sm">
        No queues found. Create a topic and bind a queue to get started.
      </TableCell>
    </TableRow>
  );
}

export default function QueueStats() {
  const [data, setData] = useState<QueueStats[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedQueue, setSelectedQueue] = useState<string | null>(null);
  const { refreshKey } = useRefresh();

  const fetchQueueStats = () => {
    setLoading(true);
    getQueueStats()
      .then(setData)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchQueueStats();
  }, [refreshKey]);

  if (error) {
    return (
      <ErrorDisplay message={`Failed to load queue stats — ${error}`} onRetry={fetchQueueStats} variant="network" compact />
    );
  }

  return (
    <section className="m-2 rounded-lg border border-border bg-card p-4 shadow-sm">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-3">
        Queue Breakdown
      </h2>

      <div className="rounded-lg border border-border overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40 hover:bg-muted/40">
              <TableHead className="font-semibold text-xs uppercase tracking-wider">
                Queue
              </TableHead>
              <TableHead className="font-semibold text-xs uppercase tracking-wider">
                Topic
              </TableHead>
              <TableHead className="font-semibold text-xs uppercase tracking-wider text-amber-600 dark:text-amber-400">
                Pending
              </TableHead>
              <TableHead className="font-semibold text-xs uppercase tracking-wider text-orange-600 dark:text-orange-400">
                Unacked
              </TableHead>
              <TableHead className="font-semibold text-xs uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
                Acked
              </TableHead>
              <TableHead className="font-semibold text-xs uppercase tracking-wider text-rose-600 dark:text-rose-400">
                Dead
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {loading ? (
              <SkeletonRows />
            ) : !data || data.length === 0 ? (
              <EmptyState />
            ) : (
              data.map((queue) => (
                <TableRow
                  key={queue.queueName}
                  onClick={() =>
                    setSelectedQueue(
                      selectedQueue === queue.queueName ? null : queue.queueName
                    )
                  }
                  className={`cursor-pointer transition-colors ${selectedQueue === queue.queueName
                    ? "bg-muted/60"
                    : "hover:bg-muted/30"
                    }`}
                >
                  <TableCell className="font-medium font-mono text-sm">
                    {queue.queueName}
                    {selectedQueue === queue.queueName && (
                      <Badge variant="outline" className="ml-2 text-xs">
                        selected
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground text-sm">
                    {queue.topicName}
                  </TableCell>
                  <StatusCell
                    value={queue.pending}
                    color="text-amber-600 dark:text-amber-400"
                  />
                  <StatusCell
                    value={queue.unacked}
                    color="text-orange-600 dark:text-orange-400"
                  />
                  <StatusCell
                    value={queue.acked}
                    color="text-emerald-600 dark:text-emerald-400"
                  />
                  <StatusCell
                    value={queue.dead}
                    color="text-rose-600 dark:text-rose-400"
                    bold={queue.dead > 0}
                  />
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {selectedQueue && (
        <div className="mt-4">
          <div className="flex items-center justify-between mb-3">
            <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
              Messages in <span className="font-mono text-foreground">{selectedQueue}</span>
            </p>
            <button
              onClick={() => setSelectedQueue(null)}
              className="text-xs text-red-600 hover:text-foreground transition-colors"
            >
              Close
            </button>
          </div>
          <QueueMessagePanel queueName={selectedQueue} />
        </div>
      )}
    </section>
  );
}