package com.airtribe.chronos.execution.domain;

public enum ExecutionStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
    RETRY_SCHEDULED,
    TERMINAL_FAILURE
}
