package com.airtribe.chronos.execution.consumer;

import com.airtribe.chronos.commons.correlation.CorrelationIds;
import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.execution.dedupe.ProcessedEventEntity;
import com.airtribe.chronos.execution.dedupe.ProcessedEventRepository;
import com.airtribe.chronos.execution.runner.ExecutionRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class JobDueConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobDueConsumer.class);
    static final String GROUP = "execution.job-due";

    private final ObjectMapper objectMapper;
    private final ExecutionRunner executionRunner;
    private final ProcessedEventRepository processedRepository;

    public JobDueConsumer(ObjectMapper objectMapper, ExecutionRunner executionRunner,
                           ProcessedEventRepository processedRepository) {
        this.objectMapper = objectMapper;
        this.executionRunner = executionRunner;
        this.processedRepository = processedRepository;
    }

    @KafkaListener(topics = Topics.JOBS_DUE, groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onJobDue(String message) throws Exception {
        JsonNode env = objectMapper.readTree(message);
        String eventId = env.path("eventId").asText();
        ProcessedEventEntity.Pk pk = new ProcessedEventEntity.Pk(eventId, GROUP);
        if (processedRepository.existsById(pk)) {
            return;
        }
        String correlationId = env.path("correlationId").asText(null);
        if (correlationId != null) MDC.put(CorrelationIds.MDC_KEY, correlationId);
        try {
            JsonNode p = env.path("payload");
            UUID jobId = UUID.fromString(p.path("jobId").asText());
            UUID ownerId = UUID.fromString(p.path("ownerId").asText());
            String taskType = p.path("taskType").asText();
            String payload = p.path("payload").asText("{}");
            int maxAttempts = p.path("maxAttempts").asInt(3);
            executionRunner.runAttempt(jobId, ownerId, taskType, payload, 1, maxAttempts, correlationId);
            processedRepository.save(new ProcessedEventEntity(eventId, GROUP));
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
