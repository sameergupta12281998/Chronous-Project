package com.airtribe.chronos.commons.correlation;

/**
 * Header name and MDC key constants for distributed correlation id propagation.
 */
public final class CorrelationIds {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIds() {
    }
}
