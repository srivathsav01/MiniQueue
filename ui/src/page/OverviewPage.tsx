import DeadLetterQueueStats from "@/components/DeadLetterQueue";
import Overview from "@/components/Overview";
import QueueStats from "@/components/QueueStats";

export default function OverviewPage() {
    return (
        <div className="flex flex-col gap-4">
            <Overview />
            <QueueStats />
            <DeadLetterQueueStats />
        </div>
    )
}