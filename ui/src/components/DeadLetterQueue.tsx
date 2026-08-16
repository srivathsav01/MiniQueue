import { getDlqMessages, replayDlqMessage } from "@/api/dashboard";
import type { DlqMessage } from "@/types/dashboard";
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
import { Button } from "@/components/ui/button";
import { Repeat2 } from 'lucide-react';
import { toast } from "sonner";
import { useConfirmDialog } from "@/context/ConfirmDialogContext";
import { useRefresh } from "@/context/RefreshContext";

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
                No Dead messages found.
            </TableCell>
        </TableRow>
    );
}

export default function DeadLetterQueueStats() {
    const [data, setData] = useState<DlqMessage[] | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [replayingId, setReplayingId] = useState<string | null>(null);
    const { openConfirmDialog } = useConfirmDialog();
    const { triggerRefresh } = useRefresh();
    const replayMessage = async (messageId: string) => {
        setReplayingId(messageId);
        try {
            await replayDlqMessage(messageId);
            setData((prevData) => prevData?.filter((msg) => msg.messageId !== messageId) || null);
            toast.success("Message reset to PENDING", {
                description: `${messageId.substring(0, 10)}... is back in the queue.`,
            });
            triggerRefresh();
        } catch (err) {
            toast.error("Failed to replay message", {
                description: err instanceof Error ? err.message : "Something went wrong.",
            });
        } finally {
            setReplayingId(null);
        }
    }

    useEffect(() => {
        setLoading(true);
        getDlqMessages()
            .then(setData)
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }, []);

    if (error) {
        return (
            <div className="rounded-lg border border-rose-200 bg-rose-50 dark:bg-rose-950/30 dark:border-rose-800 px-5 py-4 text-sm text-rose-700 dark:text-rose-400">
                Failed to load deleted messages — {error}
            </div>
        );
    }

    return (
        <section className="m-2 rounded-lg border border-border bg-card p-4 shadow-sm">
            <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-3">
                Dead Messages
            </h2>

            <div className="rounded-lg border border-border overflow-hidden">
                <Table>
                    <TableHeader>
                        <TableRow className="bg-muted/40 hover:bg-muted/40">
                            <TableHead className="font-semibold text-xs uppercase tracking-wider">
                                messageId
                            </TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider">
                                Queue Name
                            </TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider ">
                                Payload
                            </TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider ">
                                consumer ID
                            </TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider ">
                                retry Count
                            </TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider ">
                                unacked At
                            </TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider ">
                                Replay Message
                            </TableHead>
                        </TableRow>
                    </TableHeader>

                    <TableBody>
                        {loading ? (
                            <SkeletonRows />
                        ) : !data || data.length === 0 ? (
                            <EmptyState />
                        ) : (
                            data.map((msg) => (
                                <TableRow
                                    key={msg.messageId}
                                    className={"cursor-pointer transition-colors hover:bg-muted/30"}
                                    onMouseEnter={(e) => e.currentTarget.classList.add("bg-muted/20")}
                                    onMouseLeave={(e) => e.currentTarget.classList.remove("bg-muted/20")}
                                >
                                    <TableCell className="font-medium font-mono text-sm">
                                        {msg.messageId.trim().substring(0, 10) + "..."}
                                    </TableCell>
                                    <TableCell className="text-muted-foreground text-sm">
                                        {msg.queueName}
                                    </TableCell>
                                    <TableCell className="text-muted-foreground text-sm max-w-50 truncate" title={msg.payload}>
                                        {msg.payload}
                                    </TableCell>
                                    <TableCell className="text-muted-foreground text-sm">
                                        {msg.consumerId}
                                    </TableCell>
                                    <StatusCell
                                        value={msg.retryCount}
                                        color="text-orange-600 dark:text-orange-400"
                                    />
                                    <TableCell className="text-muted-foreground text-sm">
                                        {msg.unackedAt
                                            ? new Date(String(msg.unackedAt)).toLocaleString("en-GB", {
                                                day: "2-digit",
                                                month: "short",
                                                year: "numeric",
                                                hour: "2-digit",
                                                minute: "2-digit",
                                            })
                                            : "—"}
                                    </TableCell>
                                    <TableCell className="text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
                                        <Button size="sm" variant="ghost" className="flex items-center gap-2"
                                            onClick={() => openConfirmDialog({
                                                title: "Replay this message?",
                                                description: `Message ${msg.messageId.substring(0, 10)}... will be reset to PENDING and requeued.`,
                                                confirmLabel: "Replay",
                                                onConfirm: () => replayMessage(msg.messageId),
                                            })}
                                            disabled={replayingId === msg.messageId}>
                                            <Repeat2 className={`size-4 ${replayingId === msg.messageId ? "animate-spin" : ""}`} />
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))
                        )}
                    </TableBody>
                </Table>
            </div>
        </section>
    );
}