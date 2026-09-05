import { AlertTriangle, RefreshCw, WifiOff, ServerCrash, ShieldAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import Applayout from "./AppLayout";

type ErrorVariant = "default" | "network" | "server" | "notFound" | "forbidden";

interface ErrorProps {
  message?: string;
  variant?: ErrorVariant;
  onRetry?: () => void;
  retryMessage?: string;
  compact?: boolean;
}

const VARIANTS: Record<
  ErrorVariant,
  { icon: React.ElementType; title: string; color: string; bg: string; border: string }
> = {
  default: {
    icon: AlertTriangle,
    title: "Something went wrong",
    color: "text-rose-600 dark:text-rose-400",
    bg: "bg-rose-50 dark:bg-rose-950/30",
    border: "border-rose-200 dark:border-rose-800",
  },
  network: {
    icon: WifiOff,
    title: "Connection failed",
    color: "text-orange-600 dark:text-orange-400",
    bg: "bg-orange-50 dark:bg-orange-950/30",
    border: "border-orange-200 dark:border-orange-800",
  },
  server: {
    icon: ServerCrash,
    title: "Server error",
    color: "text-rose-600 dark:text-rose-400",
    bg: "bg-rose-50 dark:bg-rose-950/30",
    border: "border-rose-200 dark:border-rose-800",
  },
  notFound: {
    icon: AlertTriangle,
    title: "Not found",
    color: "text-amber-600 dark:text-amber-400",
    bg: "bg-amber-50 dark:bg-amber-950/30",
    border: "border-amber-200 dark:border-amber-800",
  },
  forbidden: {
    icon: ShieldAlert,
    title: "Access denied",
    color: "text-rose-600 dark:text-rose-400",
    bg: "bg-rose-50 dark:bg-rose-950/30",
    border: "border-rose-200 dark:border-rose-800",
  },
};

function CompactError({ message, variant = "default", onRetry, retryMessage }: ErrorProps) {
  const config = VARIANTS[variant];
  const Icon = config.icon;

  return (
    <div className="flex items-center justify-center">
      <div
        className={`md:flex items-center gap-2 rounded-lg border ${config.border} ${config.bg} px-4 py-2.5 w-[50%]`}
      >
        <Icon className={`size-3.5 flex-shrink-0 ${config.color}`} />
        <p className={`text-xs flex-1 ${config.color}`}>
          {message || config.title}
        </p>
        {onRetry && (
          <button
            onClick={onRetry}
            className={`text-xs underline underline-offset-2 flex-shrink-0 ${config.color} hover:opacity-70 transition-opacity`}
          >
            {retryMessage || "Retry"}
          </button>
        )}
      </div>
    </div>
  );
}

function FullError({ message, variant = "default", onRetry, retryMessage }: ErrorProps) {
  const config = VARIANTS[variant];
  const Icon = config.icon;

  return (
    <>
      <Applayout></Applayout>
      <div className="flex flex-col items-center justify-center py-16 gap-4 text-center px-6 border rounded-lg max-w-md mx-auto mt-16">
        <div className={`rounded-full p-3 ${config.bg} border ${config.border}`}>
          <Icon className={`size-6 ${config.color}`} />
        </div>
        <div className="space-y-1">
          <p className={`text-sm font-semibold ${config.color}`}>{config.title}</p>
          {message && (
            <p className="text-xs text-muted-foreground max-w-xs">{message}</p>
          )}
        </div>
        {onRetry && (
          <Button
            size="sm"
            variant="outline"
            onClick={onRetry}
            className="gap-1.5"
          >
            <RefreshCw className="size-3.5" />
            {retryMessage || "Try again"}
          </Button>
        )}
      </div>
    </>
  );
}

export default function ErrorDisplay({
  message,
  variant = "default",
  onRetry,
  retryMessage,
  compact = false,
}: ErrorProps) {
  if (compact) {
    return <CompactError message={message} variant={variant} onRetry={onRetry} retryMessage={retryMessage} />;
  }
  return <FullError message={message} variant={variant} onRetry={onRetry} retryMessage={retryMessage} />;
}