package com.sri.miniqueue.utils;

public interface APIConstants {

    String API_V1 = "/api/v1";
    String TOPICS = "/topics";
    String QUEUES = "/queues";
    String MESSAGES = "/messages";
    String DASHBOARD = "/dashboard";
    String CLIENTS = "/clients";

    /** Optional header that tells replicas of one client apart, e.g. "pod-2". */
    String INSTANCE_HEADER = "MiniQueue-Instance";
}
