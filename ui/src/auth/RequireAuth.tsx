import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import ErrorDisplay from "@/page/ErrorPage";

/** Shows its children only when signed in; otherwise sends the browser to the sign-in page. */
export default function RequireAuth({ children }: { children: ReactNode }) {
    const { state } = useAuth();

    if (state.status === "loading") {
        return (
            <div className="flex items-center justify-center py-24 text-sm text-muted-foreground">
                Checking your session...
            </div>
        );
    }
    if (state.status === "signedOut") {
        return <Navigate to="/login" replace />;
    }
    return <>{children}</>;
}

/**
 * Hides admin-only pages from viewers. This is for a friendly message only: the real check is
 * on the server, which answers 403 to a viewer no matter what the UI shows.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
    const { isAdmin } = useAuth();
    if (!isAdmin) {
        return (
            <div className="m-4">
                <ErrorDisplay compact variant="forbidden" message="Only admins can manage clients." />
            </div>
        );
    }
    return <>{children}</>;
}