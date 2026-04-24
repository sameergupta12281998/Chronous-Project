package com.airtribe.chronos.scheduler.dedupe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventEntity.Pk.class)
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Id
    @Column(name = "consumer_group", length = 100)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {}

    public ProcessedEventEntity(String eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
        this.processedAt = Instant.now();
    }

    public static class Pk implements Serializable {
        private String eventId;
        private String consumerGroup;
        public Pk() {}
        public Pk(String eventId, String consumerGroup) { this.eventId = eventId; this.consumerGroup = consumerGroup; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk p)) return false;
            return Objects.equals(eventId, p.eventId) && Objects.equals(consumerGroup, p.consumerGroup);
        }
        @Override public int hashCode() { return Objects.hash(eventId, consumerGroup); }
    }
}
