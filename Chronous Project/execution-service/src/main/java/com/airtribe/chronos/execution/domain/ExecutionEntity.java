package com.airtribe.chronos.execution.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "executions")
public class ExecutionEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "task_type", nullable = false, length = 100)
    private String taskType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExecutionStatus status;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    protected ExecutionEntity() {}

    public ExecutionEntity(UUID jobId, UUID ownerId, String taskType, String payload,
                            int attempt, int maxAttempts, String correlationId) {
        this.id = UUID.randomUUID();
        this.jobId = jobId;
        this.ownerId = ownerId;
        this.taskType = taskType;
        this.payload = payload;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.status = ExecutionStatus.STARTED;
        this.startedAt = Instant.now();
        this.correlationId = correlationId;
    }

    public void succeed() {
        this.status = ExecutionStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = ExecutionStatus.FAILED;
        this.error = reason;
        this.finishedAt = Instant.now();
    }

    public void scheduleRetry(Instant when, String reason) {
        this.status = ExecutionStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = when;
        this.error = reason;
        this.finishedAt = Instant.now();
    }

    public void terminal(String reason) {
        this.status = ExecutionStatus.TERMINAL_FAILURE;
        this.error = reason;
        this.finishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public UUID getOwnerId() { return ownerId; }
    public String getTaskType() { return taskType; }
    public String getPayload() { return payload; }
    public int getAttempt() { return attempt; }
    public int getMaxAttempts() { return maxAttempts; }
    public ExecutionStatus getStatus() { return status; }
    public String getError() { return error; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getCorrelationId() { return correlationId; }
}
