package com.sri.miniqueue.security;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class GitHubApiClient implements GitHubApi {

    private final RestClient rest;
    private final GitHubOAuthSettings settings;

    public GitHubApiClient(RestClient.Builder builder, GitHubOAuthSettings settings) {
        this.rest = builder.build();
        this.settings = settings;
    }

    @Override
    public GitHubUser fetchUser(String code) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", settings.clientId());
            form.add("client_secret", settings.clientSecret()); // server to server: never seen by the browser
            form.add("code", code);
            form.add("redirect_uri", settings.callbackUrl());

            Map<String, Object> tokenResponse = rest.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            // A bad or reused code still returns HTTP 200, with "error" instead of "access_token".
            Object accessToken = tokenResponse == null ? null : tokenResponse.get("access_token");
            if (!(accessToken instanceof String token) || token.isBlank()) {
                throw new GitHubLoginException("github_failed", "GitHub rejected the sign-in code");
            }

            GitHubUser user = rest.get()
                    .uri("https://api.github.com/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GitHubUser.class);
            if (user == null || user.login() == null) {
                throw new GitHubLoginException("github_failed", "GitHub returned no user");
            }
            return user; // GitHub's token goes out of scope here: we never store it
        } catch (RestClientException e) {
            throw new GitHubLoginException("github_failed", "Could not reach GitHub: " + e.getMessage());
        }
    }
}