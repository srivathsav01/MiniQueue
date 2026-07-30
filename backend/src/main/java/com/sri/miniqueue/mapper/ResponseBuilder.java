package com.sri.miniqueue.mapper;

import com.sri.miniqueue.dto.Response;
import org.springframework.stereotype.Component;

public class ResponseBuilder<T> {

    public static<T> Response<T> createSuccessData(T data) {
        Response<T> response = new Response<>();
        response.setResponseBody(data);
        response.setStatus(200);
        return response;
    }

    public static <T> Response<T> createErrorResponse(String errorMessage) {
        Response<T> response = new Response<>();
        response.setStatus(400);
        response.setMessage(errorMessage);
        return response;
    }
}
