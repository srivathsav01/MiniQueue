package com.sri.miniqueue.exception;

/** The topic, queue or message the caller named does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends CustomException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
