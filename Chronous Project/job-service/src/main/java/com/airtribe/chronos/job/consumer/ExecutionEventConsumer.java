package com.airtribe.chronos.job.consumer;

import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.job.domain.JobEntity;
import com.airtribe.chronos.job.domain.JobRepository;
import com.airtribe.chronos.job.domain.JobStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Consumes execution lifecycle events emitted by the Execution Service and projects
 * them into the local job aggregate so the Job Service can serve up-to-date status
 * without a synchronous cross-service call.
 *
 * <p>De-duplication via processed_events table makes the consumer idempotent.
 */
@Component
public class ExecutionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventConsumer.class);
    static final String GROUP = "job-service.execution-events";

    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository;
    private final ProcessedEventRepository processedEventRepository;

    public ExecutionEventConsumer(ObjectMapper objectMapper,
                                   JobRepository jobRepository,
                                   ProcessedEventRepository processedEventRepository) {
        this.objectMapper = objectMapper;
        this.jobRepository = jobRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = {
            Topics.EXECUTIONS_STARTED,
            Topics.EXECUTIONS_SUCCEEDED,
            Topics.EXECUTIONS_FAILED,
            Topics.EXECUTIONS_TERMINAL_FAILURE
    }, groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onExecutionEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventId = envelope.path("eventId").asText();
            String type = envelope.path("eventType").asText();
            String aggregateId = envelope.path("aggregateId").asText();

            ProcessedEventId pid = new ProcessedEventId(eventId, GROUP);
            if (processedEventRepository.existsById(pid)) {
                log.debug("Duplicate event {} skipped", eventId);
                return;
            }

            UUID jobId = UUID.fromString(aggregateId);
            Optional<JobEntity> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                log.warn("Received {} for unknown job {}", type, jobId);
                processedEventRepository.save(new ProcessedEventEntity(eventId, GROUP));
                return;
            }

            JobEntity job = jobOpt.get();
            switch (type) {
                case "ExecutionStarted" -> job.markStatus(JobStatus.EXECUTING);
                case "ExecutionSucceeded" -> {
                    if (job.getScheduleType() == com.airtribe.chronos.job.domain.ScheduleType.ONE_TIME) {
                        job.markStatus(JobStatus.COMPLETED);
                    } else {
                        job.markStatus(JobStatus.SCHEDULED);
                    }
                }
                case "ExecutionFailed" -> {
                    // transient failure, retry will happen, keep status EXECUTING
                }
                case "ExecutionTerminalFailure" -> job.markStatus(JobStatus.FAILED);
                default -> log.warn("Unknown execution event type: {}", type);
            }

            processedEventRepository.save(new ProcessedEventEntity(eventId, GROUP));
        } catch (Exception ex) {
            log.error("Failed to process execution event", ex);
            throw new RuntimeException(ex);
        }
    }
}
