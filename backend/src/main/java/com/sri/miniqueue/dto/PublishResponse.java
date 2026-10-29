package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** What one publish stored: one message per queue bound to the topic. */
@Getter
@Builder
public class PublishResponse {

    @JsonProperty("message_ids")
    private List<UUID> messageIds;

    /** true: this Idempotency-Key was already used, so nothing new was stored; these are the original ids. */
    private boolean duplicate;
}