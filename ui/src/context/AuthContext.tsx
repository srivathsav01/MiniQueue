import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useState,
    type ReactNode,
} from "react";
import { exchangeLoginCode, fetchMe, signOutOnServer } from "@/api/auth";
import { setUnauthorizedHandler } from "@/api/client";
import { clearToken, getToken, setToken } from "@/auth/token";

type AuthState =
    | { status: "loading" }
    | { status: "signedOut" }
    | { status: "signedIn"; username: string; scopes: string[] };

interface AuthContextValue {
    state: AuthState;
    isAdmin: boolean;
    completeSignIn: (code: string) => Promise<void>;
    signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    // With a stored token we don't know yet whether it is still valid, so start in "loading".
    const [state, setState] = useState<AuthState>(() =>
        getToken() ? { status: "loading" } : { status: "signedOut" }
    );

    useEffect(() => {
        // Any 401 from any API call (session expired, signed out elsewhere) lands here.
        setUnauthorizedHandler(() => setState({ status: "signedOut" }));

        // On page load, ask the server who this token belongs to. It may have expired overnight.
        if (getToken()) {
            fetchMe()
                .then((me) => setState({ status: "signedIn", username: me.username, scopes: me.scopes }))
                .catch(() => {
                    clearToken();
                    setState({ status: "signedOut" });
                });
        }
    }, []);

    const completeSignIn = useCallback(async (code: string) => {
        const session = await exchangeLoginCode(code);
        if (!session.token) {
            throw new Error("The server did not return a session");
        }
        setToken(session.token);
        setState({ status: "signedIn", username: session.username, scopes: session.scopes });
    }, []);

    const signOut = useCallback(async () => {
        try {
            await signOutOnServer(); // deletes the session row, so the token is dead everywhere
        } catch {
            // Already expired: there is nothing left to end on the server.
        }
        clearToken();
        setState({ status: "signedOut" });
    }, []);

    const isAdmin = state.status === "signedIn" && state.scopes.includes("admin");

    return (
        <AuthContext.Provider value={{ state, isAdmin, completeSignIn, signOut }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth(): AuthContextValue {
    const ctx = useContext(AuthContext);
    if (!ctx) {
        throw new Error("useAuth must be used inside <AuthProvider>");
    }
    return ctx;
}