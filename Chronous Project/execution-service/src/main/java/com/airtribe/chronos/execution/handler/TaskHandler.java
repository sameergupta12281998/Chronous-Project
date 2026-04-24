package com.airtribe.chronos.execution.handler;

/**
 * Pluggable task handler. Each task type registers an implementation that runs the
 * actual side effect (HTTP call, email send, ...). Throwing a {@link TaskExecutionException}
 * indicates a transient failure that should be retried; other RuntimeExceptions are
 * treated the same way for safety.
 */
public interface TaskHandler {
    String taskType();
    void execute(String jobId, String payloadJson) throws TaskExecutionException;
}
