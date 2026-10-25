import { toTitleCase } from "./utils";

export const ROUTE_CONSTANTS = {
    HOME: "home",
    LOGIN: "login",
    LIVE: "live",
    MANAGE: "manage",
    CLIENTS: "clients",
} as const;

export interface NavRoute {
    path: string;
    label: string;
    /** Shown in the header only to admins. The server enforces it either way. */
    adminOnly?: boolean;
}

export const Routes: NavRoute[] = [
    { path: "/", label: toTitleCase(ROUTE_CONSTANTS.HOME) },
    { path: "/" + ROUTE_CONSTANTS.LIVE, label: toTitleCase(ROUTE_CONSTANTS.LIVE) },
    { path: "/" + ROUTE_CONSTANTS.MANAGE, label: toTitleCase(ROUTE_CONSTANTS.MANAGE) },
    { path: "/" + ROUTE_CONSTANTS.CLIENTS, label: toTitleCase(ROUTE_CONSTANTS.CLIENTS), adminOnly: true },
];