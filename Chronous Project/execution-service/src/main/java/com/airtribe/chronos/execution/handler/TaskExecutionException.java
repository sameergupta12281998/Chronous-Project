package com.airtribe.chronos.execution.handler;

public class TaskExecutionException extends Exception {
    public TaskExecutionException(String message) { super(message); }
    public TaskExecutionException(String message, Throwable cause) { super(message, cause); }
}
