package com.airtribe.chronos.commons.error;

/**
 * Maps to HTTP 403. Authenticated user is not allowed to perform this action.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
