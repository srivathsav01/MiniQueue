import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { DashboardOverview } from "../types/dashboard";
import { getOverview } from "@/api/dashboard";
import { useRefresh } from "@/context/RefreshContext";

const statCards = (data: DashboardOverview) => [
  {
    label: "Topics",
    value: data.totalTopics,
    color: "text-foreground",
    bg: "bg-muted/40",
    border: "border-border",
    dot: "bg-slate-400",
  },
  {
    label: "Queues",
    value: data.totalQueues,
    color: "text-foreground",
    bg: "bg-muted/40",
    border: "border-border",
    dot: "bg-slate-400",
  },
  {
    label: "Total Messages",
    value: data.totalMessages,
    color: "text-foreground",
    bg: "bg-muted/40",
    border: "border-border",
    dot: "bg-slate-400",
  },
  {
    label: "Pending",
    value: data.pending,
    color: "text-amber-600 dark:text-amber-400",
    bg: "bg-amber-50 dark:bg-amber-950/30",
    border: "border-amber-200 dark:border-amber-800",
    dot: "bg-amber-400",
  },
  {
    label: "Unacked",
    value: data.unacked,
    color: "text-orange-600 dark:text-orange-400",
    bg: "bg-orange-50 dark:bg-orange-950/30",
    border: "border-orange-200 dark:border-orange-800",
    dot: "bg-orange-400",
  },
  {
    label: "Acked",
    value: data.acked,
    color: "text-emerald-600 dark:text-emerald-400",
    bg: "bg-emerald-50 dark:bg-emerald-950/30",
    border: "border-emerald-200 dark:border-emerald-800",
    dot: "bg-emerald-400",
  },
  {
    label: "Dead",
    value: data.dead,
    color: "text-rose-600 dark:text-rose-400",
    bg: "bg-rose-50 dark:bg-rose-950/30",
    border: "border-rose-200 dark:border-rose-800",
    dot: "bg-rose-500",
  },
];

function MetricCard({
  label,
  value,
  color,
  bg,
  border,
  dot,
}: {
  label: string;
  value: number;
  color: string;
  bg: string;
  border: string;
  dot: string;
}) {
  return (
    <Card className={`${bg} border ${border} shadow-none`}>
      <CardHeader className="pb-1 pt-4 px-3 md:px-5">
        <CardTitle className="flex items-center gap-2 text-xs font-medium uppercase tracking-widest text-muted-foreground">
          <span className={`hidden md:inline-block h-1.5 w-1.5 rounded-full ${dot}`} />
          {label}
        </CardTitle>
      </CardHeader>
      <CardContent className="px-5 pb-5">
        <p className={`text-4xl font-bold tabular-nums tracking-tight ${color}`}>
          {value.toLocaleString()}
        </p>
      </CardContent>
    </Card>
  );
}

function SkeletonCard() {
  return (
    <Card className="border border-border bg-muted/40 shadow-none">
      <CardHeader className="pb-1 pt-4 px-5">
        <Skeleton className="h-3 w-20" />
      </CardHeader>
      <CardContent className="px-5 pb-5">
        <Skeleton className="h-10 w-24 mt-1" />
      </CardContent>
    </Card>
  );
}

export default function Overview() {
  const [data, setData] = useState<DashboardOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const refreshKey = useRefresh();
  
  useEffect(() => {
    setLoading(true);
    getOverview()
      .then(setData)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, [refreshKey]);

  if (error) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50 dark:bg-rose-950/30 dark:border-rose-800 px-5 py-4 text-sm text-rose-700 dark:text-rose-400">
        Failed to load overview — {error}
      </div>
    );
  }

  const cards = data ? statCards(data) : [];

  return (
    <section className="m-2 rounded-lg border border-border bg-card p-4 shadow-sm">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-3">
        Broker Overview
      </h2>

      {/* Top row — infrastructure counts */}
      <div className="grid grid-cols-3 gap-3 mb-3">
        {loading
          ? Array.from({ length: 3 }).map((_, i) => <SkeletonCard key={i} />)
          : cards.slice(0, 3).map((c) => <MetricCard key={c.label} {...c} />)}
      </div>

      {/* Bottom row — message status breakdown */}
      <div className="grid grid-cols-4 gap-3">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)
          : cards.slice(3).map((c) => <MetricCard key={c.label} {...c} />)}
      </div>
    </section>
  );
}