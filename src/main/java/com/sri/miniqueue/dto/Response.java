package com.sri.miniqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

@Setter
@Getter
@ToString
public class Response<T> {

    private int status;
    private String message;
    private Set<String> errors;

    @JsonProperty("response_body")
    private T responseBody;
}
