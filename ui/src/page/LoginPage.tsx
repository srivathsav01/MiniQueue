import { LogIn, Ship } from "lucide-react";
import { Navigate, useSearchParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { gitHubSignInUrl } from "@/api/auth";
import { useAuth } from "@/context/AuthContext";

// The backend sends the browser back here with ?error=<reason> when sign-in fails.
const ERRORS: Record<string, string> = {
    cancelled: "Sign-in was cancelled on GitHub.",
    state_mismatch: "That sign-in was not started from this browser. Please try again.",
    github_failed: "GitHub could not confirm who you are. Please try again.",
    not_allowed: "This dashboard is private: your GitHub account is not an admin.",
    not_configured: "GitHub sign-in is not set up on this server yet.",
    expired: "Sign-in took too long or was already used. Please try again.",
};

export default function LoginPage() {
    const { state } = useAuth();
    const [params] = useSearchParams();
    const error = params.get("error");

    if (state.status === "signedIn") {
        return <Navigate to="/" replace />;
    }

    return (
        <div className="min-h-screen flex items-center justify-center p-4">
            <Card className="w-full max-w-sm">
                <CardHeader className="text-center space-y-2">
                    <Ship size={36} strokeWidth={1} className="mx-auto" />
                    <CardTitle className="font-serif text-xl">MiniQueue</CardTitle>
                    <CardDescription>
                        A PostgreSQL-backed message broker. Sign in with your GitHub account to look
                        around: anyone gets read-only access.
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                    {error && (
                        <p className="text-xs rounded-md border border-rose-200 dark:border-rose-800 bg-rose-50 dark:bg-rose-950/30 text-rose-600 dark:text-rose-400 px-3 py-2">
                            {ERRORS[error] ?? "Sign-in failed. Please try again."}
                        </p>
                    )}
                    <Button className="w-full" onClick={() => window.location.assign(gitHubSignInUrl())}>
                        <LogIn className="size-4 mr-1" /> Sign in with GitHub
                    </Button>
                    <p className="text-[11px] text-muted-foreground text-center">
                        MiniQueue only reads your public GitHub profile (username and id).
                    </p>
                </CardContent>
            </Card>
        </div>
    );
}