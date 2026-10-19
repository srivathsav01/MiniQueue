package com.sri.miniqueue.exception;

/**
 * The request is valid, but it conflicts with the current state: the message is not
 * UNACKED, it belongs to another consumer, the name already exists, and so on.
 * Mapped to HTTP 409. Retrying the same request will not help.
 */
public class ConflictException extends CustomException {

    public ConflictException(String message) {
        super(message);
    }
}
