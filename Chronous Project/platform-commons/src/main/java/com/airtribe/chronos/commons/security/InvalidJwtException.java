package com.airtribe.chronos.commons.security;

/**
 * Thrown when a JWT cannot be parsed or has failed validation.
 */
public class InvalidJwtException extends RuntimeException {
    public InvalidJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
