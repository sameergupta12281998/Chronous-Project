package com.airtribe.chronos.scheduler.scan;

import com.airtribe.chronos.commons.event.EventEnvelope;
import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.scheduler.domain.ScheduleEntity;
import com.airtribe.chronos.scheduler.domain.ScheduleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Periodically scans for due schedules and emits {@link Topics#JOBS_DUE} events.
 * Recurring schedules are advanced; one-time schedules are deactivated after firing.
 */
@Component
public class DueJobScanner {

    private static final Logger log = LoggerFactory.getLogger(DueJobScanner.class);
    private static final int BATCH_SIZE = 50;

    private final ScheduleRepository scheduleRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DueJobScanner(ScheduleRepository scheduleRepository,
                         KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper) {
        this.scheduleRepository = scheduleRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${chronos.scheduler.scan-interval-ms:1000}")
    @Transactional
    public void scan() {
        Instant now = Instant.now();
        List<ScheduleEntity> due = scheduleRepository.findDue(now, PageRequest.of(0, BATCH_SIZE));
        for (ScheduleEntity s : due) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("jobId", s.getJobId().toString());
                payload.put("ownerId", s.getOwnerId().toString());
                payload.put("taskType", s.getTaskType());
                payload.put("payload", s.getPayload());
                payload.put("scheduledAt", s.getNextRunAt().toString());
                payload.put("maxAttempts", s.getMaxAttempts());

                String correlationId = UUID.randomUUID().toString();
                EventEnvelope<Object> env = EventEnvelope.create("JobDue", 1, s.getJobId().toString(), correlationId, payload);
                kafkaTemplate.send(Topics.JOBS_DUE, s.getJobId().toString(), objectMapper.writeValueAsString(env)).get();

                Instant nextRun = computeNextRun(s);
                s.markFired(now, nextRun);
                log.info("Fired JobDue jobId={} nextRun={}", s.getJobId(), nextRun);
            } catch (Exception ex) {
                log.error("Failed to emit JobDue for jobId={}", s.getJobId(), ex);
            }
        }
        scheduleRepository.saveAll(due);
    }

    static Instant computeNextRun(ScheduleEntity s) {
        if (!"RECURRING".equals(s.getScheduleType()) || s.getRecurrenceFrequency() == null) {
            return null;
        }
        Instant base = s.getNextRunAt();
        return switch (s.getRecurrenceFrequency()) {
            case "MINUTE" -> base.plus(1, ChronoUnit.MINUTES);
            case "HOURLY" -> base.plus(1, ChronoUnit.HOURS);
            case "DAILY" -> base.plus(1, ChronoUnit.DAYS);
            case "WEEKLY" -> base.plus(7, ChronoUnit.DAYS);
            case "MONTHLY" -> base.plus(30, ChronoUnit.DAYS);
            default -> null;
        };
    }
}
