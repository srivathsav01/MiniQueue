package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.ExchangeRequest;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.dto.SessionResponse;
import com.sri.miniqueue.exception.UnauthorizedException;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.security.ClientPrincipal;
import com.sri.miniqueue.security.GitHubLoginException;
import com.sri.miniqueue.security.GitHubLoginService;
import com.sri.miniqueue.security.GitHubOAuthSettings;
import com.sri.miniqueue.security.OneTimeCodes;
import com.sri.miniqueue.service.DashboardSessionService;
import com.sri.miniqueue.utils.ApiKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private static final String STATE_COOKIE = "mq_oauth_state";
    private static final String STATE_COOKIE_PATH = "/api/v1/auth/github";

    private final GitHubOAuthSettings gitHubSettings;

    private final GitHubLoginService gitHubLogin;

    private final DashboardSessionService sessionService;

    private final OneTimeCodes<SessionResponse> loginCodes;

    private final OneTimeCodes<ClientPrincipal> webSocketTickets;

    @Value("${miniqueue.security.dashboard-url}")
    private String dashboardUrl;

    // GET /api/v1/auth/github/login   the dashboard's "Sign in with GitHub" button links here
    @GetMapping("/github/login")
    public ResponseEntity<Void> startGitHubLogin() {
        if (!gitHubSettings.isConfigured()) {
            return redirect(dashboardUrl + "/login?error=not_configured", null);
        }
        String state = ApiKeys.randomToken("");
        return redirect(gitHubSettings.authorizeUrl(state), stateCookie(state, Duration.ofMinutes(10)));
    }

    // GET /api/v1/auth/github/callback?code=...&state=...   GitHub sends the browser here
    @GetMapping("/github/callback")
    public ResponseEntity<Void> finishGitHubLogin(@RequestParam(required = false) String code,
                                                  @RequestParam(required = false) String state,
                                                  @RequestParam(required = false) String error,
                                                  @CookieValue(name = STATE_COOKIE, required = false) String expectedState) {
        ResponseCookie clearState = stateCookie("", Duration.ZERO);
        if (error != null) { // the user pressed Cancel on GitHub's page
            return redirect(dashboardUrl + "/login?error=cancelled", clearState);
        }
        // The state must match the cookie this browser got in step 1, or someone else started this sign-in.
        if (code == null || state == null || expectedState == null
                || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), expectedState.getBytes(StandardCharsets.UTF_8))) {
            return redirect(dashboardUrl + "/login?error=state_mismatch", clearState);
        }
        try {
            SessionResponse session = gitHubLogin.signIn(code);
            return redirect(dashboardUrl + "/login/callback?code=" + loginCodes.issue(session), clearState);
        } catch (GitHubLoginException e) {
            log.warn("GitHub sign-in failed: {}", e.getMessage());
            return redirect(dashboardUrl + "/login?error=" + e.reason(), clearState);
        }
    }

    // POST /api/v1/auth/exchange   { "code": "lc_..." } -> the session token; the code works once
    @PostMapping("/exchange")
    public Response<SessionResponse> exchange(@RequestBody ExchangeRequest request) {
        return ResponseBuilder.createSuccessData(loginCodes.redeem(request.getCode())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired login code. Sign in again.")));
    }

    // GET /api/v1/auth/me   lets the dashboard check a stored token on page load and learn its role
    @GetMapping("/me")
    public Response<SessionResponse> me(@AuthenticationPrincipal ClientPrincipal caller) {
        return ResponseBuilder.createSuccessData(SessionResponse.builder()
                .username(caller.name())
                .scopes(List.copyOf(caller.scopes()))
                .build());
    }

    // POST /api/v1/auth/logout
    @PostMapping("/logout")
    public Response<String> logout(@AuthenticationPrincipal ClientPrincipal caller) {
        // For a session the principal id is the session id; for an API key it matches nothing (a no-op).
        sessionService.logout(caller.id());
        return ResponseBuilder.createSuccessData("Signed out");
    }

    // POST /api/v1/auth/ws-ticket   then open wss://host/ws/monitor?ticket=... within 30 seconds
    @PostMapping("/ws-ticket")
    public Response<Map<String, Object>> webSocketTicket(@AuthenticationPrincipal ClientPrincipal caller) {
        return ResponseBuilder.createSuccessData(Map.of(
                "ticket", webSocketTickets.issue(caller),
                "expires_in_seconds", webSocketTickets.ttl().toSeconds()));
    }

    private ResponseCookie stateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)                              // page scripts cannot read it
                .secure(gitHubSettings.secureCookies())      // https only in production
                .sameSite("Lax")                             // sent on the top-level redirect back from GitHub
                .path(STATE_COOKIE_PATH)                     // sent only to the two sign-in endpoints
                .maxAge(maxAge)
                .build();
    }

    private static ResponseEntity<Void> redirect(String url, ResponseCookie cookie) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url));
        if (cookie != null) {
            response.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return response.build();
    }
}