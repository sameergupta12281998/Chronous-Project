package com.airtribe.chronos.commons.error;

/**
 * Maps to HTTP 409. Operation conflicts with current resource state
 * (e.g. invalid state transition, duplicate key).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
