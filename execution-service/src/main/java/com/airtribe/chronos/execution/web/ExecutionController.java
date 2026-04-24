package com.airtribe.chronos.execution.web;

import com.airtribe.chronos.commons.error.ForbiddenException;
import com.airtribe.chronos.commons.error.NotFoundException;
import com.airtribe.chronos.commons.security.AuthenticatedUser;
import com.airtribe.chronos.execution.domain.ExecutionEntity;
import com.airtribe.chronos.execution.domain.ExecutionRepository;
import com.airtribe.chronos.execution.domain.ExecutionStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/executions")
public class ExecutionController {

    private final ExecutionRepository repository;

    public ExecutionController(ExecutionRepository repository) { this.repository = repository; }

    @GetMapping("/{id}")
    public ExecutionResponse get(@PathVariable UUID id) {
        UUID owner = currentUser();
        ExecutionEntity e = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution not found: " + id));
        if (!e.getOwnerId().equals(owner)) {
            throw new ForbiddenException("You don't own this execution");
        }
        return ExecutionResponse.of(e);
    }

    @GetMapping
    public ListResponse list(@RequestParam(required = false) UUID jobId) {
        UUID owner = currentUser();
        if (jobId == null) {
            throw new com.airtribe.chronos.commons.error.BadRequestException("jobId query parameter is required");
        }
        List<ExecutionEntity> rows = repository.findByJobIdOrderByAttemptAsc(jobId);
        // Authorization: ensure all returned rows belong to the requester
        if (rows.stream().anyMatch(e -> !e.getOwnerId().equals(owner))) {
            throw new ForbiddenException("You don't own this job's executions");
        }
        return new ListResponse(rows.stream().map(ExecutionResponse::of).toList());
    }

    private static UUID currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthenticatedUser u)) {
            throw new ForbiddenException("Not authenticated");
        }
        return u.userId();
    }

    public record ExecutionResponse(UUID id, UUID jobId, UUID ownerId, String taskType,
                                     int attempt, int maxAttempts, ExecutionStatus status,
                                     String error, Instant startedAt, Instant finishedAt,
                                     Instant nextAttemptAt, String correlationId) {
        public static ExecutionResponse of(ExecutionEntity e) {
            return new ExecutionResponse(e.getId(), e.getJobId(), e.getOwnerId(), e.getTaskType(),
                    e.getAttempt(), e.getMaxAttempts(), e.getStatus(), e.getError(),
                    e.getStartedAt(), e.getFinishedAt(), e.getNextAttemptAt(), e.getCorrelationId());
        }
    }

    public record ListResponse(List<ExecutionResponse> items) {}
}
