package com.airtribe.chronos.scheduler.consumer;

import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.scheduler.dedupe.ProcessedEventEntity;
import com.airtribe.chronos.scheduler.dedupe.ProcessedEventRepository;
import com.airtribe.chronos.scheduler.domain.ScheduleEntity;
import com.airtribe.chronos.scheduler.domain.ScheduleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class JobLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobLifecycleConsumer.class);
    static final String GROUP = "scheduler.job-lifecycle";

    private final ObjectMapper objectMapper;
    private final ScheduleRepository scheduleRepository;
    private final ProcessedEventRepository processedRepository;

    public JobLifecycleConsumer(ObjectMapper objectMapper,
                                 ScheduleRepository scheduleRepository,
                                 ProcessedEventRepository processedRepository) {
        this.objectMapper = objectMapper;
        this.scheduleRepository = scheduleRepository;
        this.processedRepository = processedRepository;
    }

    @KafkaListener(topics = {
            Topics.JOBS_CREATED, Topics.JOBS_CANCELLED, Topics.JOBS_RESCHEDULED
    }, groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onJobLifecycleEvent(String message) throws Exception {
        JsonNode env = objectMapper.readTree(message);
        String eventId = env.path("eventId").asText();
        String type = env.path("eventType").asText();
        ProcessedEventEntity.Pk pk = new ProcessedEventEntity.Pk(eventId, GROUP);
        if (processedRepository.existsById(pk)) return;

        JsonNode payload = env.path("payload");
        switch (type) {
            case "JobCreated" -> handleCreated(payload);
            case "JobCancelled" -> handleCancelled(payload);
            case "JobRescheduled" -> handleRescheduled(payload);
            default -> log.warn("Unknown lifecycle event type: {}", type);
        }
        processedRepository.save(new ProcessedEventEntity(eventId, GROUP));
    }

    private void handleCreated(JsonNode p) {
        UUID jobId = UUID.fromString(p.path("jobId").asText());
        if (scheduleRepository.findByJobId(jobId).isPresent()) return;
        ScheduleEntity s = new ScheduleEntity(
                jobId,
                UUID.fromString(p.path("ownerId").asText()),
                p.path("taskType").asText(),
                p.path("payload").asText("{}"),
                p.path("scheduleType").asText(),
                p.path("recurrenceFrequency").isNull() ? null : p.path("recurrenceFrequency").asText(),
                Instant.parse(p.path("scheduledAt").asText()),
                p.path("maxAttempts").asInt(3)
        );
        scheduleRepository.save(s);
        log.info("Scheduled job {} at {}", jobId, s.getNextRunAt());
    }

    private void handleCancelled(JsonNode p) {
        UUID jobId = UUID.fromString(p.path("jobId").asText());
        Optional<ScheduleEntity> existing = scheduleRepository.findByJobId(jobId);
        existing.ifPresent(s -> { s.deactivate(); scheduleRepository.save(s); });
    }

    private void handleRescheduled(JsonNode p) {
        UUID jobId = UUID.fromString(p.path("jobId").asText());
        Instant newTime = Instant.parse(p.path("newScheduledAt").asText());
        scheduleRepository.findByJobId(jobId).ifPresent(s -> {
            s.reschedule(newTime);
            scheduleRepository.save(s);
        });
    }
}
