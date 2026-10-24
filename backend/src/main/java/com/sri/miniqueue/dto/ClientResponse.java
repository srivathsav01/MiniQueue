package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientResponse {

    private UUID id;

    private String name;

    private List<String> scopes;

    @JsonProperty("key_prefix")
    private String keyPrefix;

    /** The full key. Only in the create and rotate responses, and never retrievable again. */
    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("last_used_at")
    private LocalDateTime lastUsedAt;
}