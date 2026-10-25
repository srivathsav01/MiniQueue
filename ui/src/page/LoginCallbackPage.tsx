import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";

/**
 * GitHub -> backend -> here, with ?code=lc_... (a one-time login code, valid for 60 seconds).
 * Trade it for the session token, then go to the dashboard.
 */
export default function LoginCallbackPage() {
    const { completeSignIn } = useAuth();
    const [params] = useSearchParams();
    const navigate = useNavigate();
    const started = useRef(false);

    useEffect(() => {
        // The code works exactly once. In development, React StrictMode runs every effect twice,
        // and a second exchange would fail and bounce the user back to sign-in. Hence the guard.
        if (started.current) {
            return;
        }
        started.current = true;

        const code = params.get("code");
        if (!code) {
            navigate("/login?error=expired", { replace: true });
            return;
        }
        // replace: true also removes the URL with the code from the browser history.
        completeSignIn(code)
            .then(() => navigate("/", { replace: true }))
            .catch(() => navigate("/login?error=expired", { replace: true }));
    }, [params, completeSignIn, navigate]);

    return (
        <div className="min-h-screen flex items-center justify-center text-sm text-muted-foreground">
            Signing you in...
        </div>
    );
}