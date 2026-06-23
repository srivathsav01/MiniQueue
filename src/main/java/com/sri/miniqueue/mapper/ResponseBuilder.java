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
}
