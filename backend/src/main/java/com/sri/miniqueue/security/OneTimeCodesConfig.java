package com.sri.miniqueue.security;

import com.sri.miniqueue.dto.SessionResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OneTimeCodesConfig {

    /** ?ticket= on a WebSocket handshake. */
    @Bean
    public OneTimeCodes<ClientPrincipal> webSocketTickets() {
        return new OneTimeCodes<>("wst_", Duration.ofSeconds(30));
    }

    /** ?code= on the redirect back to the dashboard after GitHub sign-in. */
    @Bean
    public OneTimeCodes<SessionResponse> loginCodes() {
        return new OneTimeCodes<>("lc_", Duration.ofSeconds(60));
    }
}