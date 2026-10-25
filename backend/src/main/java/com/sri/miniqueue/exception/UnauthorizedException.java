package com.sri.miniqueue.exception;

/** A login code or credential that is invalid or expired. Mapped to HTTP 401. */
public class UnauthorizedException extends CustomException {

    public UnauthorizedException(String message) {
        super(message);
    }
}