package com.airtribe.chronos.job.web;

import com.airtribe.chronos.job.domain.JobEntity;
import com.airtribe.chronos.job.domain.JobStatus;
import com.airtribe.chronos.job.domain.RecurrenceFrequency;
import com.airtribe.chronos.job.domain.ScheduleType;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        String taskType,
        String payload,
        ScheduleType scheduleType,
        RecurrenceFrequency recurrenceFrequency,
        Instant scheduledAt,
        JobStatus status,
        int maxAttempts,
        Instant createdAt,
        Instant updatedAt
) {
    public static JobResponse of(JobEntity j) {
        return new JobResponse(j.getId(), j.getOwnerId(), j.getName(), j.getDescription(), j.getTaskType(),
                j.getPayloadJson(), j.getScheduleType(), j.getRecurrenceFrequency(), j.getScheduledAt(),
                j.getStatus(), j.getMaxAttempts(), j.getCreatedAt(), j.getUpdatedAt());
    }
}
