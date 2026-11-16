package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateQueueRequest {

    /** Seconds, 5 to 43200. Null resets the queue to the broker default. */
    @JsonProperty("lease_seconds")
    private Integer leaseSeconds;
}
