package com.sri.miniqueue.security;

/** The two calls we make to GitHub. An interface, so tests can stand in for GitHub. */
public interface GitHubApi {

    record GitHubUser(long id, String login) {
    }

    /** Trades a sign-in code for GitHub's token, asks who it belongs to, and forgets the token. */
    GitHubUser fetchUser(String code);
}