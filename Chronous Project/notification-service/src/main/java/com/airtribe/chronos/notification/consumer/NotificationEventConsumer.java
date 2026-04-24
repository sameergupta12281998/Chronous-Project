package com.airtribe.chronos.notification.consumer;

import com.airtribe.chronos.commons.event.EventEnvelope;
import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.notification.dedupe.ProcessedEventEntity;
import com.airtribe.chronos.notification.dedupe.ProcessedEventRepository;
import com.airtribe.chronos.notification.dispatch.NotificationDispatcher;
import com.airtribe.chronos.notification.domain.NotificationEntity;
import com.airtribe.chronos.notification.domain.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    static final String GROUP = "notification.events";

    private final ObjectMapper objectMapper;
    private final NotificationRepository repository;
    private final ProcessedEventRepository processedRepository;
    private final NotificationDispatcher dispatcher;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public NotificationEventConsumer(ObjectMapper objectMapper, NotificationRepository repository,
                                      ProcessedEventRepository processedRepository,
                                      NotificationDispatcher dispatcher,
                                      KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.processedRepository = processedRepository;
        this.dispatcher = dispatcher;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = {
            Topics.JOBS_CREATED, Topics.EXECUTIONS_SUCCEEDED,
            Topics.EXECUTIONS_FAILED, Topics.EXECUTIONS_TERMINAL_FAILURE
    }, groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onEvent(String message) throws Exception {
        JsonNode env = objectMapper.readTree(message);
        String eventId = env.path("eventId").asText();
        String type = env.path("eventType").asText();
        if (processedRepository.existsById(new ProcessedEventEntity.Pk(eventId, GROUP))) return;

        JsonNode p = env.path("payload");
        UUID jobId = parseUuid(p.path("jobId").asText(null));
        UUID ownerId = parseUuid(p.path("ownerId").asText(null));
        if (ownerId == null || jobId == null) return;

        String text = switch (type) {
            case "JobCreated" -> "Your job " + jobId + " was scheduled";
            case "ExecutionSucceeded" -> "Job " + jobId + " execution succeeded";
            case "ExecutionFailed" -> "Job " + jobId + " execution failed (will retry)";
            case "ExecutionTerminalFailure" -> "Job " + jobId + " has permanently failed: " + p.path("error").asText("");
            default -> null;
        };
        if (text == null) return;

        NotificationEntity n = new NotificationEntity(ownerId, jobId, type, text);
        repository.save(n);
        dispatcher.dispatch(n);
        n.markDispatched();
        repository.save(n);

        Map<String, Object> ev = new HashMap<>();
        ev.put("notificationId", n.getId().toString());
        ev.put("ownerId", ownerId.toString());
        ev.put("jobId", jobId.toString());
        ev.put("type", type);
        EventEnvelope<Object> outEnv = EventEnvelope.create("NotificationDispatched", 1, n.getId().toString(),
                env.path("correlationId").asText(null), ev);
        kafkaTemplate.send(Topics.NOTIFICATIONS_DISPATCHED, n.getId().toString(),
                objectMapper.writeValueAsString(outEnv));

        processedRepository.save(new ProcessedEventEntity(eventId, GROUP));
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
