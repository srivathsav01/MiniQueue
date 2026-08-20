import { toTitleCase } from "./utils";

export const ROUTE_CONSTANTS = {
    HOME: "home",
    LOGIN: "login",
    LIVE: "live"
} as const;

export const Routes = [
    { path: "/", label: toTitleCase(ROUTE_CONSTANTS.HOME) },
    { path: "/" + ROUTE_CONSTANTS.LIVE, label: toTitleCase(ROUTE_CONSTANTS.LIVE) },
    // { path: "/" + ROUTE_CONSTANTS.ANALYSE, label: toTitleCase(ROUTES.ANALYSE) },
    // { path: "/" + ROUTE_CONSTANTS.SETTINGS, label: toTitleCase(ROUTES.SETTINGS) },
];