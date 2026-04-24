package com.airtribe.chronos.execution.runner;

import com.airtribe.chronos.commons.event.EventEnvelope;
import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.execution.domain.ExecutionEntity;
import com.airtribe.chronos.execution.domain.ExecutionRepository;
import com.airtribe.chronos.execution.handler.TaskExecutionException;
import com.airtribe.chronos.execution.handler.TaskHandler;
import com.airtribe.chronos.execution.handler.TaskHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Core execution engine: runs the configured handler for an attempt and emits the
 * appropriate Kafka events. Retry decisions and terminal failures live here.
 */
@Component
public class ExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRunner.class);

    private final TaskHandlerRegistry registry;
    private final ExecutionRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ExecutionRunner(TaskHandlerRegistry registry, ExecutionRepository repository,
                            KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.registry = registry;
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExecutionEntity runAttempt(UUID jobId, UUID ownerId, String taskType, String payload,
                                       int attempt, int maxAttempts, String correlationId) {
        ExecutionEntity exec = new ExecutionEntity(jobId, ownerId, taskType, payload, attempt, maxAttempts, correlationId);
        repository.save(exec);
        emit("ExecutionStarted", Topics.EXECUTIONS_STARTED, exec, correlationId, null);

        try {
            TaskHandler handler = registry.handlerFor(taskType);
            handler.execute(jobId.toString(), payload);
            exec.succeed();
            repository.save(exec);
            emit("ExecutionSucceeded", Topics.EXECUTIONS_SUCCEEDED, exec, correlationId, null);
        } catch (TaskExecutionException | RuntimeException ex) {
            log.warn("Execution attempt {} of job {} failed", attempt, jobId, ex);
            if (attempt >= maxAttempts) {
                exec.terminal(ex.getMessage());
                repository.save(exec);
                emit("ExecutionTerminalFailure", Topics.EXECUTIONS_TERMINAL_FAILURE, exec, correlationId, ex.getMessage());
            } else {
                Instant nextAttempt = Instant.now().plus(backoff(attempt));
                exec.scheduleRetry(nextAttempt, ex.getMessage());
                repository.save(exec);
                emit("ExecutionFailed", Topics.EXECUTIONS_FAILED, exec, correlationId, ex.getMessage());
            }
        }
        return exec;
    }

    static Duration backoff(int attempt) {
        long seconds = (long) Math.min(60, Math.pow(2, attempt));
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    private void emit(String type, String topic, ExecutionEntity e, String correlationId, String error) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("executionId", e.getId().toString());
            payload.put("jobId", e.getJobId().toString());
            payload.put("ownerId", e.getOwnerId().toString());
            payload.put("attempt", e.getAttempt());
            payload.put("maxAttempts", e.getMaxAttempts());
            payload.put("status", e.getStatus().name());
            payload.put("error", error);
            payload.put("nextAttemptAt", e.getNextAttemptAt() != null ? e.getNextAttemptAt().toString() : null);
            EventEnvelope<Object> env = EventEnvelope.create(type, 1, e.getJobId().toString(), correlationId, payload);
            kafkaTemplate.send(topic, e.getJobId().toString(), objectMapper.writeValueAsString(env));
        } catch (Exception ex) {
            log.error("Failed to emit event {} for execution {}", type, e.getId(), ex);
        }
    }
}
