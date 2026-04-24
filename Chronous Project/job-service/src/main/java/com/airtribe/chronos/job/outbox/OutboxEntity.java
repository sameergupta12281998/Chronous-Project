package com.airtribe.chronos.job.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_outbox")
public class OutboxEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected OutboxEntity() {
    }

    public OutboxEntity(UUID aggregateId, String eventType, String topic, String payloadJson, String correlationId) {
        this.id = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payloadJson = payloadJson;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
        this.attemptCount = 0;
    }

    public void markSent() {
        this.sentAt = Instant.now();
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getTopic() { return topic; }
    public String getPayloadJson() { return payloadJson; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public int getAttemptCount() { return attemptCount; }
}
