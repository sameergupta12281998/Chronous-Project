package com.airtribe.chronos.job.consumer;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * De-duplication record for Kafka consumers. Composite key on (event_id, consumer_group)
 * ensures the same event isn't processed twice, regardless of broker re-delivery.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Id
    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(String eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public String getConsumerGroup() { return consumerGroup; }
    public Instant getProcessedAt() { return processedAt; }
}
