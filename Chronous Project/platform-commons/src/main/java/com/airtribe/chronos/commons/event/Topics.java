package com.airtribe.chronos.commons.event;

/**
 * Canonical Kafka topic names. Versioned (.v1) so contracts can evolve without breaking consumers.
 */
public final class Topics {
    public static final String JOBS_CREATED = "chronos.jobs.created.v1";
    public static final String JOBS_CANCELLED = "chronos.jobs.cancelled.v1";
    public static final String JOBS_RESCHEDULED = "chronos.jobs.rescheduled.v1";
    public static final String JOBS_DUE = "chronos.jobs.due.v1";

    public static final String EXECUTIONS_STARTED = "chronos.executions.started.v1";
    public static final String EXECUTIONS_SUCCEEDED = "chronos.executions.succeeded.v1";
    public static final String EXECUTIONS_FAILED = "chronos.executions.failed.v1";
    public static final String EXECUTIONS_TERMINAL_FAILURE = "chronos.executions.terminal-failure.v1";

    public static final String NOTIFICATIONS_DISPATCHED = "chronos.notifications.dispatched.v1";

    private Topics() {
    }
}
