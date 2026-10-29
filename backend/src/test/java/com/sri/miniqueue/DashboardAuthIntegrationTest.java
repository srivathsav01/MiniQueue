package com.sri.miniqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.security.GitHubApi;
import com.sri.miniqueue.service.ClientService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole sign-in flow through the real security chain, with GitHub itself replaced by a mock. */
@SpringBootTest(properties = {
        "miniqueue.security.github.client-id=test-client-id",
        "miniqueue.security.github.client-secret=test-client-secret",
        "miniqueue.security.admins=srivathsav01",
        "miniqueue.security.dashboard-url=http://dash.test"
})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
@Testcontainers
class DashboardAuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ClientService clientService;

    @MockitoBean
    private GitHubApi gitHub;

    @Test
    void signInStartsAtGitHubWithAStateCookie() throws Exception {
        mvc.perform(get("/api/v1/auth/github/login"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://github.com/login/oauth/authorize")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("client_id=test-client-id")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("mq_oauth_state=")));
    }

    @Test
    void aCallbackWhoseStateDoesNotMatchTheCookieNeverReachesGitHub() throws Exception {
        mvc.perform(get("/api/v1/auth/github/callback").param("code", "c").param("state", "attacker")
                        .cookie(new Cookie("mq_oauth_state", "mine")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://dash.test/login?error=state_mismatch"));
        verifyNoInteractions(gitHub);
    }

    @Test
    void aListedAdminCanManageTheBroker() throws Exception {
        String admin = signInAs("srivathsav01", 1);
        mvc.perform(get("/api/v1/clients").header(AUTHORIZATION, "Bearer " + admin)).andExpect(status().isOk());
    }

    @Test
    void anyOtherGitHubUserCanReadButNotChangeAnything() throws Exception {
        String viewer = signInAs("some-recruiter", 2);

        mvc.perform(get("/api/v1/dashboard/overview").header(AUTHORIZATION, "Bearer " + viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/topics").header(AUTHORIZATION, "Bearer " + viewer)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"nope\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/clients").header(AUTHORIZATION, "Bearer " + viewer)).andExpect(status().isForbidden());
    }

    @Test
    void aLoginCodeWorksExactlyOnce() throws Exception {
        String code = loginCodeFor("someone", 3);
        exchange(code).andExpect(status().isOk());
        exchange(code).andExpect(status().isUnauthorized());
    }

    @Test
    void signingOutEndsTheSessionImmediately() throws Exception {
        String token = signInAs("someone-else", 4);
        mvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void aWebSocketTicketWorksExactlyOnce() throws Exception {
        String viewer = signInAs("watcher", 5);
        String body = mvc.perform(post("/api/v1/auth/ws-ticket").header(AUTHORIZATION, "Bearer " + viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ticket = json.readTree(body).path("response_body").path("ticket").asText();

        // MockMvc cannot finish a real WebSocket upgrade, so the first call ends in a 400 from the
        // handshake handler. What matters: security let it through, so it is not a 401.
        int first = mvc.perform(get("/ws/monitor").param("ticket", ticket)).andReturn().getResponse().getStatus();
        assertNotEquals(401, first);
        mvc.perform(get("/ws/monitor").param("ticket", ticket)).andExpect(status().isUnauthorized());
    }

    @Test
    void theApiIsClosedWithoutCredentials() throws Exception {
        mvc.perform(get("/api/v1/topics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dashboard/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/some/path/nobody/listed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void prometheusNeedsAMetricsKey() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String metricsKey = clientService.createClient("prom-" + suffix, List.of("metrics")).getApiKey();
        String publisherKey = clientService.createClient("pub-" + suffix, List.of("publish:*")).getApiKey();

        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, "Bearer " + publisherKey)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, "Bearer " + metricsKey)).andExpect(status().isOk());
    }

    /** Plays GitHub's part of the flow: the callback with a matching state, then the code exchange. */
    private String loginCodeFor(String login, long githubId) throws Exception {
        when(gitHub.fetchUser("code-" + login)).thenReturn(new GitHubApi.GitHubUser(githubId, login));
        String location = mvc.perform(get("/api/v1/auth/github/callback")
                        .param("code", "code-" + login).param("state", "s1")
                        .cookie(new Cookie("mq_oauth_state", "s1")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://dash.test/login/callback?code=")))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    }

    private org.springframework.test.web.servlet.ResultActions exchange(String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/exchange").contentType(APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private String signInAs(String login, long githubId) throws Exception {
        String body = exchange(loginCodeFor(login, githubId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("response_body").path("token").asText();
    }
}