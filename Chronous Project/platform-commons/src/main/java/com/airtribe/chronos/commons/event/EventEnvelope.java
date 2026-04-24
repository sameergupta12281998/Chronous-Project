package com.airtribe.chronos.commons.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned envelope wrapping every Kafka event published by Chronos services.
 * Payload is intentionally a free-form JSON node so producers and consumers can
 * evolve their payload contracts independently of the envelope.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String correlationId,
        String aggregateId,
        T payload
) {
    @JsonCreator
    public EventEnvelope(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("payload") T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public static <T> EventEnvelope<T> create(String eventType, int schemaVersion,
                                              String aggregateId, String correlationId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                schemaVersion,
                Instant.now(),
                correlationId,
                aggregateId,
                payload
        );
    }
}
