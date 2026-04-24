package com.airtribe.chronos.commons.error;

import java.time.Instant;
import java.util.List;

/**
 * Stable, version-agnostic API error contract used by every Chronos service.
 *
 * @param status     HTTP status code
 * @param error      short error code (e.g. NOT_FOUND, VALIDATION_FAILED)
 * @param message    human-readable explanation
 * @param path       request path
 * @param timestamp  server time of the error
 * @param correlationId distributed correlation id for tracing
 * @param details    optional list of field-level error messages
 */
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        String correlationId,
        List<String> details
) {
    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(status, error, message, path, Instant.now(), correlationId, List.of());
    }

    public static ApiError of(int status, String error, String message, String path,
                              String correlationId, List<String> details) {
        return new ApiError(status, error, message, path, Instant.now(), correlationId, details);
    }
}
