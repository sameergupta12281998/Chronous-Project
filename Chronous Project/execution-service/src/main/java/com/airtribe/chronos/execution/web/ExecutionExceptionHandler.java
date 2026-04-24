package com.airtribe.chronos.execution.web;

import com.airtribe.chronos.commons.correlation.CorrelationIds;
import com.airtribe.chronos.commons.error.ApiError;
import com.airtribe.chronos.commons.error.BadRequestException;
import com.airtribe.chronos.commons.error.ForbiddenException;
import com.airtribe.chronos.commons.error.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ExecutionExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> forbidden(ForbiddenException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), req);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> bad(BadRequestException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "Missing required parameter: " + ex.getParameterName(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error: " + ex.getClass().getSimpleName(), req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiError.of(
                status.value(), code, message, req.getRequestURI(), MDC.get(CorrelationIds.MDC_KEY), List.of()));
    }
}
