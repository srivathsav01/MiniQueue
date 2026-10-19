package com.sri.miniqueue.mapper;

import com.sri.miniqueue.dto.Response;

public class ResponseBuilder<T> {

    public static <T> Response<T> createSuccessData(T data) {
        Response<T> response = new Response<>();
        response.setResponseBody(data);
        response.setStatus(200);
        return response;
    }

    /** Kept for existing callers; defaults to 400. */
    public static <T> Response<T> createErrorResponse(String errorMessage) {
        return createErrorResponse(400, errorMessage);
    }

    /** The status in the body always matches the real HTTP status of the response. */
    public static <T> Response<T> createErrorResponse(int status, String errorMessage) {
        Response<T> response = new Response<>();
        response.setStatus(status);
        response.setMessage(errorMessage);
        return response;
    }
}
