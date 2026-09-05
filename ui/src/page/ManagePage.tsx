import { useEffect, useState } from "react";
import { Plus, Send } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogFooter,
    DialogDescription,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Topic } from "@/types/dashboard";
import { createQueue, createTopic, getAllTopics, publishMessage } from "@/api/management";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import ErrorDisplay from "./ErrorPage";

function SkeletonCards() {
    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {Array.from({ length: 6 }).map((_, i) => (
                <Card key={i} className="shadow-none border border-border">
                    <CardHeader className="pb-2 pt-4 px-5">
                        <Skeleton className="h-4 w-32" />
                    </CardHeader>
                    <CardContent className="px-5 pb-4">
                        <Skeleton className="h-3 w-20" />
                    </CardContent>
                </Card>
            ))}
        </div>
    );
}

function EmptyState({ onOpen }: { onOpen: () => void }) {
    return (
        <div className="flex flex-col items-center justify-center py-24 gap-3 text-muted-foreground">
            <span className="text-3xl">📭</span>
            <p className="text-sm">No topics yet.</p>
            <Button size="sm" onClick={onOpen}>
                <Plus className="size-4 mr-1" /> Create your first topic
            </Button>
        </div>
    );
}

const PAYLOAD_TEMPLATES = [
    {
        label: "Order placed",
        value: JSON.stringify({ orderId: "123", amount: 500, currency: "EUR" }, null, 2),
    },
    {
        label: "User signup",
        value: JSON.stringify({ userId: "u-456", email: "user@example.com", plan: "free" }, null, 2),
    },
    {
        label: "Payment failed",
        value: JSON.stringify({ paymentId: "p-789", reason: "insufficient_funds", retryAt: "2026-08-27T10:00:00" }, null, 2),
    },
];

export default function ManagePage() {
    const [topics, setTopics] = useState<Topic[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [topicDialogOpen, setTopicDialogOpen] = useState(false);
    const [queueDialogOpen, setQueueDialogOpen] = useState(false);
    const [messageDialogOpen, setMessageDialogOpen] = useState(false);
    const [queueName, setQueueName] = useState("");
    const [selectedTopic, setSelectedTopic] = useState("");
    const [topicName, setTopicName] = useState("");
    const [creating, setCreating] = useState(false);
    const [payload, setPayload] = useState("");
    const [publishing, setPublishing] = useState(false);

    const fetchTopics = () => {
        setLoading(true);
        getAllTopics()
            .then(setTopics)
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        fetchTopics();
    }, []);

    const handleTopicCreate = async () => {
        if (!topicName.trim()) return;
        setCreating(true);
        try {
            await createTopic(topicName.trim());
            toast.success("Topic created", { description: topicName.trim() });
            setTopicName("");
            setTopicDialogOpen(false);
            fetchTopics();
        } catch (err: unknown) {
            toast.error("Failed to create topic", {
                description: err instanceof Error ? err.message : "Something went wrong",
            });
        } finally {
            setCreating(false);
        }
    };

    const handleQueueCreate = async () => {
        if (!queueName.trim() || !selectedTopic) return;
        setCreating(true);
        try {
            await createQueue(queueName.trim(), selectedTopic);
            toast.success("Queue created", {
                description: `${queueName.trim()} → ${selectedTopic}`,
            });
            setQueueName("");
            setSelectedTopic("");
            setQueueDialogOpen(false);
        } catch (err: unknown) {
            toast.error("Failed to create queue", {
                description: err instanceof Error ? err.message : "Something went wrong",
            });
        } finally {
            setCreating(false);
        }
    };

    const handlePublish = async () => {
        if (!selectedTopic || !payload.trim()) return;
        setPublishing(true);
        try {
            await publishMessage(selectedTopic, payload.trim());
            toast.success("Message published", {
                description: `Published to ${selectedTopic}`,
            });
            setPayload("");
            setSelectedTopic("");
            setMessageDialogOpen(false);
        } catch (err: unknown) {
            toast.error("Failed to publish", {
                description: err instanceof Error ? err.message : "Something went wrong",
            });
        } finally {
            setPublishing(false);
        }
    };

    const isValidJson = (str: string) => {
        try {
            JSON.parse(str);
            return true;
        } catch {
            return false;
        }
    };

    const payloadValid = payload.trim() && isValidJson(payload.trim());

    if (error) {
        return (
            <ErrorDisplay message={`Failed to load topics — ${error}`} onRetry={fetchTopics} variant="network" compact />
        );
    }

    return (
        <section className="space-y-4 m-2 rounded-lg border border-border bg-card p-4 shadow-sm">
            {/* Header */}
            <div className="md:flex items-center justify-between">
                <div>
                    <h1 className="text-base font-semibold">Topics</h1>
                    <p className="text-xs text-muted-foreground mt-0.5">
                        Manage broker topics and their bindings
                    </p>
                </div>
                {topics.length > 0 && (
                    <div className="flex items-center gap-2 mt-3 md:mt-0">
                        <Button size="sm" onClick={() => setTopicDialogOpen(true)}>
                            <Plus className="size-4 mr-1" /> New Topic
                        </Button>
                        <Button size="sm" onClick={() => setQueueDialogOpen(true)}>
                            <Plus className="size-4 mr-1" /> New Queue
                        </Button>
                        <Button size="sm" onClick={() => setMessageDialogOpen(true)}>
                            <Plus className="size-4 mr-1" /> Publish Message
                        </Button>
                    </div>
                )}
            </div>

            {/* Topic grid */}
            {loading ? (
                <SkeletonCards />
            ) : topics.length === 0 ? (
                <EmptyState onOpen={() => setTopicDialogOpen(true)} />
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                    {topics.map((topic) => (
                        <Card
                            key={topic.id}
                            className="shadow-none border border-border hover:border-border/80 transition-colors"
                        >
                            <CardHeader className="pb-2 pt-4 px-5">
                                <CardTitle className="text-sm font-semibold font-serif">
                                    {topic.name}
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="px-5 pb-4">
                                <Badge variant="outline" className="text-xs font-mono font-extralight">
                                    {topic.id}
                                </Badge>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            )}


            <Dialog open={messageDialogOpen} onOpenChange={(open) => {
                if (!open) { setPayload(""); setSelectedTopic(""); }
                setMessageDialogOpen(open);
            }}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Publish Message</DialogTitle>
                        <DialogDescription>
                            Publish a message to a topic — it fans out to all bound queues
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3 py-2">
                        <div className="space-y-1.5">
                            <Label>Topic</Label>
                            <Select
                                value={selectedTopic}
                                onValueChange={(value) => setSelectedTopic(value ?? "")}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="Select a topic" />
                                </SelectTrigger>
                                <SelectContent>
                                    {topics.map((t) => (
                                        <SelectItem key={t.id} value={t.name}>
                                            {t.name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        {/* Payload templates */}
                        <div className="space-y-1.5">
                            <Label>Payload Templates</Label>
                            <div className="flex gap-2 flex-wrap">
                                {PAYLOAD_TEMPLATES.map((tpl) => (
                                    <Button
                                        key={tpl.label}
                                        variant="outline"
                                        size="sm"
                                        className="text-xs"
                                        onClick={() => setPayload(tpl.value)}
                                    >
                                        {tpl.label}
                                    </Button>
                                ))}
                            </div>
                        </div>

                        {/* Payload editor */}
                        <div className="space-y-1.5">
                            <div className="flex items-center justify-between">
                                <Label htmlFor="payload">Payload (JSON)</Label>
                                {payload && (
                                    <span
                                        className={`text-xs ${payloadValid
                                            ? "text-emerald-600 dark:text-emerald-400"
                                            : "text-rose-600 dark:text-rose-400"
                                            }`}
                                    >
                                        {payloadValid ? "Valid JSON" : "Invalid JSON"}
                                    </span>
                                )}
                            </div>
                            <Textarea
                                id="payload"
                                placeholder='{ "key": "value" }'
                                value={payload}
                                onChange={(e) => setPayload(e.target.value)}
                                className="font-mono text-sm min-h-[180px] resize-y"
                            />
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => { setMessageDialogOpen(false); setPayload(""); setSelectedTopic(""); }}>
                            Cancel
                        </Button>
                        <Button
                            onClick={handlePublish}
                            disabled={publishing || !selectedTopic || !payloadValid}
                            className=""
                        >
                            <Send className="size-4 mr-2" />
                            {publishing ? "Publishing..." : "Publish Message"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
            <Dialog open={topicDialogOpen} onOpenChange={setTopicDialogOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Create Topic</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-3 py-2">
                        <div className="space-y-1.5">
                            <Label htmlFor="topic-name">Topic Name</Label>
                            <Input
                                id="topic-name"
                                placeholder="e.g. order.placed"
                                value={topicName}
                                onChange={(e) => setTopicName(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && handleTopicCreate()}
                            />
                            <p className="text-xs text-muted-foreground">
                                Use dot notation for namespacing — e.g. order.placed, payment.failed
                            </p>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => { setTopicDialogOpen(false); setTopicName(""); }}>
                            Cancel
                        </Button>
                        <Button onClick={handleTopicCreate} disabled={creating || !topicName.trim()}>
                            {creating ? "Creating..." : "Create"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
            <Dialog open={queueDialogOpen} onOpenChange={(open) => {
                if (!open) { setQueueName(""); setSelectedTopic(""); }
                setQueueDialogOpen(open);
            }}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Create Queue</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-3 py-2">
                        <div className="space-y-1.5">
                            <Label htmlFor="queue-name">Queue Name</Label>
                            <Input
                                id="queue-name"
                                placeholder="e.g. email-queue"
                                value={queueName}
                                onChange={(e) => setQueueName(e.target.value)}
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Bind to Topic</Label>
                            <Select
                                value={selectedTopic}
                                onValueChange={(value) => setSelectedTopic(value ?? "")}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="Select a topic" />
                                </SelectTrigger>
                                <SelectContent>
                                    {topics.map((t) => (
                                        <SelectItem key={t.id} value={t.name}>
                                            {t.name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => { setQueueDialogOpen(false); setQueueName(""); setSelectedTopic(""); }}>
                            Cancel
                        </Button>
                        <Button
                            onClick={handleQueueCreate}
                            disabled={creating || !queueName.trim() || !selectedTopic}
                        >
                            {creating ? "Creating..." : "Create"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </section>
    );
}