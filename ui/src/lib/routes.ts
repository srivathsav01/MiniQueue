import { toTitleCase } from "./utils";

export const ROUTE_CONSTANTS = {
    HOME: "home",
    LOGIN: "login",
    LIVE: "live",
    MANAGE: "manage",
} as const;

export const Routes = [
    { path: "/", label: toTitleCase(ROUTE_CONSTANTS.HOME) },
    { path: "/" + ROUTE_CONSTANTS.LIVE, label: toTitleCase(ROUTE_CONSTANTS.LIVE) },
    { path: "/" + ROUTE_CONSTANTS.MANAGE, label: toTitleCase(ROUTE_CONSTANTS.MANAGE) },
    // { path: "/" + ROUTE_CONSTANTS.SETTINGS, label: toTitleCase(ROUTES.SETTINGS) },
];