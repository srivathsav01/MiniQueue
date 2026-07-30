package com.sri.miniqueue.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString
public class TopicResponse {

        private UUID id;

        private String name;
}
