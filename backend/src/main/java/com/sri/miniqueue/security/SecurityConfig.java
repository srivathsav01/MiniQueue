package com.sri.miniqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.service.ClientService;
import com.sri.miniqueue.utils.Scopes;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.util.Locale;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ClientService clientService;

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        JsonSecurityErrors errors = new JsonSecurityErrors(objectMapper);

        http
                // CSRF tricks a browser into sending its cookies to another site. API keys travel
                // in a header that a malicious page cannot make the browser add, so CSRF does not
                // apply here. Step 4 adds a cookie session for the admin and revisits this.
                .csrf(AbstractHttpConfigurer::disable)
                // Reuse CorsConfig, and let browser preflight (OPTIONS) requests through unauthenticated.
                .cors(Customizer.withDefaults())
                // Every request carries its own key: never create an HTTP session.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .addFilterBefore(new ApiKeyAuthenticationFilter(clientService, errors), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/clients", "/api/v1/clients/**").hasAuthority(Scopes.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/topics/{topic}/messages").access(scope(Scopes.PUBLISH, "topic"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/queues/{queue}/consume").access(scope(Scopes.CONSUME, "queue"))
                        // Any valid key. The service then checks that this caller holds the message.
                        .requestMatchers(HttpMethod.POST, "/api/v1/messages/*/ack", "/api/v1/messages/*/nack").authenticated()
                        // Topic/queue management, dashboard, Swagger, actuator and /ws stay open
                        // until Step 4 puts them behind the admin login.
                        .anyRequest().permitAll());

        return http.build();
    }

    /** Allows the request if the caller's scopes cover this action on the topic or queue named in the URL. */
    private static AuthorizationManager<RequestAuthorizationContext> scope(String action, String pathVariable) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (auth == null || !(auth.getPrincipal() instanceof ClientPrincipal client)) {
                return new AuthorizationDecision(false); // anonymous: Spring turns this into 401
            }
            // Names are stored lowercase, so "Orders" in the URL must match "publish:orders".
            String name = context.getVariables().getOrDefault(pathVariable, "").toLowerCase(Locale.ROOT);
            return new AuthorizationDecision(Scopes.allows(client.scopes(), action, name));
        };
    }
}