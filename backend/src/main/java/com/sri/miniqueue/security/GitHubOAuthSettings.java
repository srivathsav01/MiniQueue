package com.sri.miniqueue.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class GitHubOAuthSettings {

    private final String clientId;
    private final String clientSecret;
    private final String callbackUrl;

    public GitHubOAuthSettings(@Value("${miniqueue.security.github.client-id:}") String clientId,
                               @Value("${miniqueue.security.github.client-secret:}") String clientSecret,
                               @Value("${miniqueue.security.github.callback-url}") String callbackUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackUrl = callbackUrl;
        if (!isConfigured()) {
            log.warn("GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET are not set: dashboard sign-in is disabled");
        }
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    /** Asks for no OAuth scopes: the public profile (id and login) is all we need. */
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    /** Secure cookies only work over https, so they are off for http://localhost. */
    public boolean secureCookies() {
        return callbackUrl.startsWith("https://");
    }

    String clientId() {
        return clientId;
    }

    String clientSecret() {
        return clientSecret;
    }

    String callbackUrl() {
        return callbackUrl;
    }
}