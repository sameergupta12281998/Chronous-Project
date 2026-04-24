package com.airtribe.chronos.scheduler.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedules")
public class ScheduleEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "task_type", nullable = false, length = 100)
    private String taskType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "schedule_type", nullable = false, length = 20)
    private String scheduleType;

    @Column(name = "recurrence_frequency", length = 20)
    private String recurrenceFrequency;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ScheduleEntity() {
    }

    public ScheduleEntity(UUID jobId, UUID ownerId, String taskType, String payload,
                           String scheduleType, String recurrenceFrequency,
                           Instant nextRunAt, int maxAttempts) {
        this.id = UUID.randomUUID();
        this.jobId = jobId;
        this.ownerId = ownerId;
        this.taskType = taskType;
        this.payload = payload;
        this.scheduleType = scheduleType;
        this.recurrenceFrequency = recurrenceFrequency;
        this.nextRunAt = nextRunAt;
        this.maxAttempts = maxAttempts;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void reschedule(Instant newTime) {
        this.nextRunAt = newTime;
        this.updatedAt = Instant.now();
    }

    public void markFired(Instant fireTime, Instant nextRun) {
        this.lastRunAt = fireTime;
        if (nextRun == null) {
            this.active = false;
        } else {
            this.nextRunAt = nextRun;
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public UUID getOwnerId() { return ownerId; }
    public String getTaskType() { return taskType; }
    public String getPayload() { return payload; }
    public String getScheduleType() { return scheduleType; }
    public String getRecurrenceFrequency() { return recurrenceFrequency; }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public int getMaxAttempts() { return maxAttempts; }
    public boolean isActive() { return active; }
}
