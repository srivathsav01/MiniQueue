package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Who the caller is. The token and expiry are only present in the exchange response. */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionResponse {

    private String token;

    private String username;

    private List<String> scopes;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;
}