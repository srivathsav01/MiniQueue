package com.sri.miniqueue.exception;

/** The message payload is over miniqueue.limits.max-payload-bytes. Mapped to HTTP 413. */
public class PayloadTooLargeException extends CustomException{

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
