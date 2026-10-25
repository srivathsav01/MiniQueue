// The dashboard session token (mqs_...). Kept in localStorage so a page refresh or a new tab
// stays signed in. The server expires it after 8 hours, and signing out deletes it there too.
//
// localStorage is readable by any script on this page, so an XSS bug could steal it. An
// HttpOnly cookie would be safer, but the dashboard and the API are different sites on
// onrender.com, and browsers block cookies between sites. The short expiry and server-side
// sign-out limit the damage.

const KEY = "miniqueue.session";

// Fallback for browsers where storage is blocked (some private modes): the session then lasts
// until the page is reloaded.
let inMemory: string | null = null;

export function getToken(): string | null {
    try {
        return localStorage.getItem(KEY) ?? inMemory;
    } catch {
        return inMemory;
    }
}

export function setToken(token: string): void {
    inMemory = token;
    try {
        localStorage.setItem(KEY, token);
    } catch {
        // storage blocked: keep the in-memory copy only
    }
}

export function clearToken(): void {
    inMemory = null;
    try {
        localStorage.removeItem(KEY);
    } catch {
        // nothing stored
    }
}