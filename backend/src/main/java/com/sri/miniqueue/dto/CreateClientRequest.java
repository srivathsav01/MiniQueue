package com.sri.miniqueue.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateClientRequest {

    private String name;

    private List<String> scopes;
}