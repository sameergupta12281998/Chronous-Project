package com.airtribe.chronos.commons.error;

/**
 * Maps to HTTP 400. The request payload or parameters are invalid.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
