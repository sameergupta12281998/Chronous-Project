package com.airtribe.chronos.job.web;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleRequest(@NotNull Instant newScheduledAt) {
}
