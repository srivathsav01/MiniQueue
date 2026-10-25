import { useCallback, useEffect, useState } from "react";
import { Copy, KeyRound, Plus, RotateCw, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { createClient, listClients, revokeClient, rotateClientKey } from "@/api/clients";
import { useConfirmDialog } from "@/context/ConfirmDialogContext";
import type { BrokerClient } from "@/types/dashboard";

// Quick-add buttons for the most common scopes.
const SCOPE_HINTS = ["publish:*", "consume:*", "metrics", "admin"];

/**
 * The server sends LocalDateTime without a time zone, so converting it to the browser's zone
 * would only be right by luck. Show it as the server wrote it.
 */
function formatServerTime(value?: string): string {
    return value ? value.replace("T", " ").slice(0, 16) : "Never";
}

/** Splits "publish:orders, consume:email-queue" or one-per-line input into a clean list. */
function parseScopes(input: string): string[] {
    return input.split(/[\s,]+/).map((s) => s.trim()).filter(Boolean);
}

export default function ClientsPage() {
    const { openConfirmDialog } = useConfirmDialog();
    const [clients, setClients] = useState<BrokerClient[]>([]);
    const [loading, setLoading] = useState(true);

    const [createOpen, setCreateOpen] = useState(false);
    const [name, setName] = useState("");
    const [scopesText, setScopesText] = useState("");
    const [saving, setSaving] = useState(false);

    // The client whose new key is on screen. Cleared when that dialog closes: the key is never shown again.
    const [issued, setIssued] = useState<BrokerClient | null>(null);

    // Reloads keep the current table on screen (no skeleton flash); only the first load shows one.
    const load = useCallback(() => {
        listClients()
            .then(setClients)
            .catch(() => setClients([])) // the error toast comes from the API client
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const handleCreate = async () => {
        setSaving(true);
        try {
            const created = await createClient(name.trim(), parseScopes(scopesText));
            setCreateOpen(false);
            setName("");
            setScopesText("");
            setIssued(created);
            load();
        } catch {
            // 400 (bad name or scope) and 409 (name taken) are shown by the API client's toast
        } finally {
            setSaving(false);
        }
    };

    const handleRotate = (client: BrokerClient) =>
        openConfirmDialog({
            title: `Rotate the key for ${client.name}?`,
            description: "The current key stops working immediately. Update the service's configuration with the new key straight away.",
            confirmLabel: "Rotate",
            variant: "destructive",
            onConfirm: async () => {
                try {
                    setIssued(await rotateClientKey(client.name));
                    load();
                } catch {
                    // toast shown by the API client
                }
            },
        });

    const handleRevoke = (client: BrokerClient) =>
        openConfirmDialog({
            title: `Revoke ${client.name}?`,
            description: "Its key stops working immediately and the service can no longer publish or consume. The name becomes free to reuse.",
            confirmLabel: "Revoke",
            variant: "destructive",
            onConfirm: async () => {
                try {
                    await revokeClient(client.name);
                    toast.success(`${client.name} revoked`);
                    load();
                } catch {
                    // toast shown by the API client
                }
            },
        });

    const copyKey = async () => {
        if (!issued?.api_key) return;
        try {
            await navigator.clipboard.writeText(issued.api_key);
            toast.success("Key copied");
        } catch {
            toast.error("Could not copy. Select the key and copy it by hand.");
        }
    };

    return (
        <section className="space-y-4 m-2 rounded-lg border border-border bg-card p-4 shadow-sm">
            <div className="md:flex items-center justify-between">
                <div>
                    <h1 className="text-base font-semibold">Clients</h1>
                    <p className="text-xs text-muted-foreground mt-0.5">
                        Applications that publish or consume, and the API keys they authenticate with
                    </p>
                </div>
                <Button size="sm" className="mt-3 md:mt-0" onClick={() => setCreateOpen(true)}>
                    <Plus className="size-4 mr-1" /> Register client
                </Button>
            </div>

            <div className="rounded-lg border border-border overflow-hidden">
                <Table>
                    <TableHeader>
                        <TableRow className="bg-muted/40 hover:bg-muted/40">
                            <TableHead className="font-semibold text-xs uppercase tracking-wider">Name</TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider">Scopes</TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider">Key</TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider">Created</TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider">Last used</TableHead>
                            <TableHead className="font-semibold text-xs uppercase tracking-wider text-right">Actions</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {loading ? (
                            Array.from({ length: 3 }).map((_, i) => (
                                <TableRow key={i}>
                                    <TableCell colSpan={6}><Skeleton className="h-4 w-full" /></TableCell>
                                </TableRow>
                            ))
                        ) : clients.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={6} className="text-center text-sm text-muted-foreground py-10">
                                    No clients yet. Register one for each service that publishes or consumes.
                                </TableCell>
                            </TableRow>
                        ) : (
                            clients.map((client) => (
                                <TableRow key={client.id}>
                                    <TableCell className="font-medium">{client.name}</TableCell>
                                    <TableCell>
                                        <div className="flex flex-wrap gap-1">
                                            {client.scopes.map((scope) => (
                                                <Badge key={scope} variant="outline" className="font-mono text-xs font-extralight">
                                                    {scope}
                                                </Badge>
                                            ))}
                                        </div>
                                    </TableCell>
                                    <TableCell className="font-mono text-xs">{client.key_prefix}...</TableCell>
                                    <TableCell className="text-xs">{formatServerTime(client.created_at)}</TableCell>
                                    <TableCell className="text-xs">{formatServerTime(client.last_used_at)}</TableCell>
                                    <TableCell className="text-right whitespace-nowrap">
                                        <Button size="sm" variant="ghost" onClick={() => handleRotate(client)} title="Rotate key">
                                            <RotateCw className="size-4" />
                                        </Button>
                                        <Button size="sm" variant="ghost" onClick={() => handleRevoke(client)} title="Revoke">
                                            <Trash2 className="size-4 text-rose-600" />
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))
                        )}
                    </TableBody>
                </Table>
            </div>

            {/* Register */}
            <Dialog open={createOpen} onOpenChange={setCreateOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Register a client</DialogTitle>
                        <DialogDescription>
                            One client per service. Its replicas share the key; the SDK tells them apart.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3 py-2">
                        <div className="space-y-1.5">
                            <Label htmlFor="client-name">Name</Label>
                            <Input
                                id="client-name"
                                placeholder="e.g. email-service"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label htmlFor="client-scopes">Scopes</Label>
                            <Textarea
                                id="client-scopes"
                                placeholder={"publish:order.placed\nconsume:email-queue"}
                                value={scopesText}
                                onChange={(e) => setScopesText(e.target.value)}
                                rows={3}
                                className="font-mono text-xs"
                            />
                            <div className="flex flex-wrap gap-1">
                                {SCOPE_HINTS.map((hint) => (
                                    <Button key={hint} size="xs" variant="outline" className="font-mono"
                                        onClick={() => setScopesText((t) => (t.trim() ? `${t.trim()}\n${hint}` : hint))}>
                                        {hint}
                                    </Button>
                                ))}
                            </div>
                            <p className="text-xs text-muted-foreground">
                                publish:&lt;topic&gt;, consume:&lt;queue&gt;, * for any name, metrics, view or admin.
                                Grant the least the service needs.
                            </p>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setCreateOpen(false)}>Cancel</Button>
                        <Button onClick={handleCreate} disabled={saving || !name.trim() || !scopesText.trim()}>
                            {saving ? "Registering..." : "Register"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* The new key, shown once */}
            <Dialog open={issued !== null} onOpenChange={(open) => !open && setIssued(null)}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <KeyRound className="size-4" /> API key for {issued?.name}
                        </DialogTitle>
                        <DialogDescription>
                            Copy it now. MiniQueue stores only a fingerprint of the key, so it cannot show it again.
                            If it is lost, rotate it.
                        </DialogDescription>
                    </DialogHeader>
                    <div className="flex items-center gap-2 py-2">
                        <code className="flex-1 break-all rounded-md border border-border bg-muted/40 px-3 py-2 text-xs font-mono select-all">
                            {issued?.api_key}
                        </code>
                        <Button size="icon" variant="outline" onClick={copyKey} title="Copy">
                            <Copy className="size-4" />
                        </Button>
                    </div>
                    <p className="text-xs text-muted-foreground">
                        Give it to the service through its environment, e.g. MINIQUEUE_API_KEY. Never commit it.
                    </p>
                    <DialogFooter>
                        <Button onClick={() => setIssued(null)}>I have saved it</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </section>
    );
}