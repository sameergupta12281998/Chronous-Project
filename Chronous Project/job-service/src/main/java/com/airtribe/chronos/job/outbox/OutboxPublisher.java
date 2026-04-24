package com.airtribe.chronos.job.outbox;

import com.airtribe.chronos.commons.correlation.CorrelationIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the outbox table and publishes pending events to Kafka.
 * Uses at-least-once delivery; downstream consumers MUST be idempotent.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${chronos.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEntity> pending = outboxRepository.findUnsent(PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) return;

        for (OutboxEntity entry : pending) {
            try {
                MDC.put(CorrelationIds.MDC_KEY, entry.getCorrelationId());
                kafkaTemplate.send(entry.getTopic(), entry.getAggregateId().toString(), entry.getPayloadJson()).get();
                entry.markSent();
                log.info("Outbox published eventId={} type={} topic={}", entry.getId(), entry.getEventType(), entry.getTopic());
            } catch (Exception ex) {
                entry.incrementAttempt();
                log.error("Outbox publish failed eventId={} type={} attempt={}", entry.getId(), entry.getEventType(), entry.getAttemptCount(), ex);
            } finally {
                MDC.remove(CorrelationIds.MDC_KEY);
            }
        }
        outboxRepository.saveAll(pending);
    }
}
