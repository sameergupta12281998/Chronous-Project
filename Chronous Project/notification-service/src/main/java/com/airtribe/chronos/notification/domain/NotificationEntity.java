package com.airtribe.chronos.notification.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    protected NotificationEntity() {}

    public NotificationEntity(UUID ownerId, UUID jobId, String type, String message) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.jobId = jobId;
        this.type = type;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public void markDispatched() { this.dispatchedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getJobId() { return jobId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
}
