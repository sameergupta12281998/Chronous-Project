package com.airtribe.chronos.commons.error;

/**
 * Maps to HTTP 404. Resource does not exist or is not visible to the caller.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
