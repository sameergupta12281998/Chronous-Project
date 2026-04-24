package com.airtribe.chronos.job.web;

import com.airtribe.chronos.job.domain.RecurrenceFrequency;
import com.airtribe.chronos.job.domain.ScheduleType;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateJobRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 100) String taskType,
        String payload,
        @NotNull ScheduleType scheduleType,
        RecurrenceFrequency recurrenceFrequency,
        @NotNull Instant scheduledAt,
        @Min(1) @Max(20) Integer maxAttempts
) {
    public int safeMaxAttempts() {
        return maxAttempts == null ? 3 : maxAttempts;
    }
}
