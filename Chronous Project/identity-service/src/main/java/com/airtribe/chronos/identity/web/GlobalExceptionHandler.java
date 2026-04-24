package com.airtribe.chronos.identity.web;

import com.airtribe.chronos.commons.correlation.CorrelationIds;
import com.airtribe.chronos.commons.error.ApiError;
import com.airtribe.chronos.commons.error.BadRequestException;
import com.airtribe.chronos.commons.error.ConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", req, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error: " + ex.getClass().getSimpleName(), req, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest req, List<String> details) {
        String corr = MDC.get(CorrelationIds.MDC_KEY);
        return ResponseEntity.status(status).body(
                ApiError.of(status.value(), code, message, req.getRequestURI(), corr, details));
    }
}
