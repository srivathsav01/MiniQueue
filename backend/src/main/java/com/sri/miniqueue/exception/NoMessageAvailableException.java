package com.sri.miniqueue.exception;

/**
 * Nothing is claimable in the queue right now. With FOR UPDATE SKIP LOCKED this can also
 * mean every remaining PENDING message is momentarily locked by other consumers.
 *
 * This is a normal outcome, not an error, so it is mapped to HTTP 204 No Content.
 * Clients should simply poll again later.
 */
public class NoMessageAvailableException extends CustomException {

    public NoMessageAvailableException(String message) {
        super(message);
    }
}
