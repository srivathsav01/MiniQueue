package com.sri.miniqueue.security;

/** Sign-in failed. The reason is a short code shown on the dashboard's login page. */
public class GitHubLoginException extends RuntimeException {

    private final String reason;

    public GitHubLoginException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}