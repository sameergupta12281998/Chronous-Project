package com.airtribe.chronos.job.consumer;

import java.io.Serializable;
import java.util.Objects;

public class ProcessedEventId implements Serializable {
    private String eventId;
    private String consumerGroup;

    public ProcessedEventId() {}
    public ProcessedEventId(String eventId, String consumerGroup) {
        this.eventId = eventId; this.consumerGroup = consumerGroup;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessedEventId p)) return false;
        return Objects.equals(eventId, p.eventId) && Objects.equals(consumerGroup, p.consumerGroup);
    }
    @Override public int hashCode() { return Objects.hash(eventId, consumerGroup); }
}
