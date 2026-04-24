package com.airtribe.chronos.job.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class JobEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "task_type", nullable = false, length = 100)
    private String taskType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private ScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency", length = 20)
    private RecurrenceFrequency recurrenceFrequency;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected JobEntity() {
    }

    public JobEntity(UUID id, UUID ownerId, String name, String description, String taskType,
                     String payloadJson, ScheduleType scheduleType, RecurrenceFrequency recurrenceFrequency,
                     Instant scheduledAt, int maxAttempts) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.taskType = taskType;
        this.payloadJson = payloadJson;
        this.scheduleType = scheduleType;
        this.recurrenceFrequency = recurrenceFrequency;
        this.scheduledAt = scheduledAt;
        this.maxAttempts = maxAttempts;
        this.status = JobStatus.SCHEDULED;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void cancel() {
        if (status == JobStatus.COMPLETED || status == JobStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel job in status " + status);
        }
        this.status = JobStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void reschedule(Instant newTime) {
        if (status != JobStatus.SCHEDULED) {
            throw new IllegalStateException("Can only reschedule a SCHEDULED job, current status: " + status);
        }
        this.scheduledAt = newTime;
        this.updatedAt = Instant.now();
    }

    public void markStatus(JobStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getTaskType() { return taskType; }
    public String getPayloadJson() { return payloadJson; }
    public ScheduleType getScheduleType() { return scheduleType; }
    public RecurrenceFrequency getRecurrenceFrequency() { return recurrenceFrequency; }
    public Instant getScheduledAt() { return scheduledAt; }
    public JobStatus getStatus() { return status; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
