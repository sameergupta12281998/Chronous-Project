package com.airtribe.chronos.job.service;

import com.airtribe.chronos.commons.correlation.CorrelationIds;
import com.airtribe.chronos.commons.error.BadRequestException;
import com.airtribe.chronos.commons.error.ForbiddenException;
import com.airtribe.chronos.commons.error.NotFoundException;
import com.airtribe.chronos.commons.event.EventEnvelope;
import com.airtribe.chronos.commons.event.Topics;
import com.airtribe.chronos.job.domain.*;
import com.airtribe.chronos.job.idempotency.IdempotencyKeyEntity;
import com.airtribe.chronos.job.idempotency.IdempotencyKeyRepository;
import com.airtribe.chronos.job.outbox.OutboxEntity;
import com.airtribe.chronos.job.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

    private final JobRepository jobRepository;
    private final OutboxRepository outboxRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public JobService(JobRepository jobRepository, OutboxRepository outboxRepository,
                       IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.outboxRepository = outboxRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobEntity createJob(UUID ownerId, String name, String description, String taskType,
                                String payloadJson, ScheduleType scheduleType,
                                RecurrenceFrequency recurrenceFrequency, Instant scheduledAt, int maxAttempts,
                                String idempotencyKey) {
        if (idempotencyKey != null) {
            if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
                throw new BadRequestException("Idempotency-Key must be 1-" + MAX_IDEMPOTENCY_KEY_LENGTH + " chars");
            }
            var existing = idempotencyKeyRepository.findById(
                    new IdempotencyKeyEntity.Pk(idempotencyKey, ownerId));
            if (existing.isPresent()) {
                JobEntity job = jobRepository.findById(existing.get().getJobId())
                        .orElseThrow(() -> new NotFoundException("Idempotent record points to missing job"));
                log.info("Idempotent replay for key={} ownerId={} returning existing jobId={}",
                        idempotencyKey, ownerId, job.getId());
                return job;
            }
        }
        if (scheduledAt == null || scheduledAt.isBefore(Instant.now().minusSeconds(1))) {
            throw new BadRequestException("scheduledAt must not be in the past");
        }
        if (scheduleType == ScheduleType.RECURRING && recurrenceFrequency == null) {
            throw new BadRequestException("recurrenceFrequency is required for RECURRING jobs");
        }
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new BadRequestException("maxAttempts must be between 1 and 20");
        }

        JobEntity job = new JobEntity(UUID.randomUUID(), ownerId, name, description, taskType,
                payloadJson, scheduleType, recurrenceFrequency, scheduledAt, maxAttempts);
        job = jobRepository.save(job);
        if (idempotencyKey != null) {
            try {
                idempotencyKeyRepository.save(new IdempotencyKeyEntity(idempotencyKey, ownerId, job.getId()));
            } catch (DataIntegrityViolationException dup) {
                // Concurrent duplicate — re-read and return the winning job
                var winning = idempotencyKeyRepository.findById(
                        new IdempotencyKeyEntity.Pk(idempotencyKey, ownerId));
                if (winning.isPresent()) {
                    return jobRepository.findById(winning.get().getJobId())
                            .orElseThrow(() -> dup);
                }
                throw dup;
            }
        }
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("jobId", job.getId().toString());
        payload.put("ownerId", ownerId.toString());
        payload.put("taskType", taskType);
        payload.put("scheduleType", scheduleType.name());
        payload.put("recurrenceFrequency", recurrenceFrequency != null ? recurrenceFrequency.name() : null);
        payload.put("scheduledAt", scheduledAt.toString());
        payload.put("maxAttempts", maxAttempts);
        payload.put("payload", payloadJson == null ? "{}" : payloadJson);
        publish("JobCreated", Topics.JOBS_CREATED, job.getId(), payload);
        return job;
    }

    @Transactional(readOnly = true)
    public Page<JobEntity> listJobs(UUID ownerId, JobStatus filter, Pageable pageable) {
        if (filter == null) {
            return jobRepository.findByOwnerId(ownerId, pageable);
        }
        return jobRepository.findByOwnerIdAndStatus(ownerId, filter, pageable);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<JobEntity> findExistingIdempotent(UUID ownerId, String idempotencyKey) {
        return idempotencyKeyRepository.findById(new IdempotencyKeyEntity.Pk(idempotencyKey, ownerId))
                .flatMap(rec -> jobRepository.findById(rec.getJobId()));
    }

    @Transactional(readOnly = true)
    public JobEntity getJob(UUID ownerId, UUID jobId) {
        JobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
        if (!job.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You don't own this job");
        }
        return job;
    }

    @Transactional
    public JobEntity cancelJob(UUID ownerId, UUID jobId) {
        JobEntity job = getJob(ownerId, jobId);
        try {
            job.cancel();
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
        publish("JobCancelled", Topics.JOBS_CANCELLED, job.getId(), Map.of(
                "jobId", job.getId().toString(),
                "ownerId", ownerId.toString()
        ));
        return job;
    }

    @Transactional
    public JobEntity rescheduleJob(UUID ownerId, UUID jobId, Instant newTime) {
        if (newTime == null || newTime.isBefore(Instant.now())) {
            throw new BadRequestException("newScheduledAt must be in the future");
        }
        JobEntity job = getJob(ownerId, jobId);
        try {
            job.reschedule(newTime);
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
        publish("JobRescheduled", Topics.JOBS_RESCHEDULED, job.getId(), Map.of(
                "jobId", job.getId().toString(),
                "ownerId", ownerId.toString(),
                "newScheduledAt", newTime.toString()
        ));
        return job;
    }

    private void publish(String type, String topic, UUID aggregateId, Object payload) {
        try {
            String correlationId = MDC.get(CorrelationIds.MDC_KEY);
            EventEnvelope<Object> env = EventEnvelope.create(type, 1, aggregateId.toString(), correlationId, payload);
            String json = objectMapper.writeValueAsString(env);
            outboxRepository.save(new OutboxEntity(aggregateId, type, topic, json, correlationId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event " + type, e);
        }
    }
}
