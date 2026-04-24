package com.airtribe.chronos.job.web;

import com.airtribe.chronos.commons.error.BadRequestException;
import com.airtribe.chronos.job.domain.JobEntity;
import com.airtribe.chronos.job.domain.JobStatus;
import com.airtribe.chronos.job.security.AuthenticatedUserResolver;
import com.airtribe.chronos.job.service.JobService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/jobs")
public class JobController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final JobService jobService;
    private final AuthenticatedUserResolver currentUser;

    public JobController(JobService jobService, AuthenticatedUserResolver currentUser) {
        this.jobService = jobService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public JobResponse create(@Valid @RequestBody CreateJobRequest req,
                              @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
                              HttpServletResponse response) {
        UUID ownerId = currentUser.requireUserId();
        boolean preExisting = false;
        if (idempotencyKey != null) {
            preExisting = jobService.findExistingIdempotent(ownerId, idempotencyKey).isPresent();
        }
        JobEntity job = jobService.createJob(ownerId, req.name(), req.description(), req.taskType(),
                req.payload(), req.scheduleType(), req.recurrenceFrequency(), req.scheduledAt(),
                req.safeMaxAttempts(), idempotencyKey);
        response.setStatus(preExisting ? HttpStatus.OK.value() : HttpStatus.CREATED.value());
        return JobResponse.of(job);
    }

    @GetMapping
    public PageResponse<JobResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) JobStatus status) {
        if (size > 100) {
            throw new BadRequestException("size must be <= 100");
        }
        UUID ownerId = currentUser.requireUserId();
        Page<JobEntity> result = jobService.listJobs(ownerId, status,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return new PageResponse<>(
                result.getContent().stream().map(JobResponse::of).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements()
        );
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return JobResponse.of(jobService.getJob(currentUser.requireUserId(), id));
    }

    @DeleteMapping("/{id}")
    public JobResponse cancel(@PathVariable UUID id) {
        return JobResponse.of(jobService.cancelJob(currentUser.requireUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancelViaPost(@PathVariable UUID id) {
        return cancel(id);
    }

    @PatchMapping("/{id}/reschedule")
    public JobResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest req) {
        return JobResponse.of(jobService.rescheduleJob(currentUser.requireUserId(), id, req.newScheduledAt()));
    }

    @PostMapping("/{id}/reschedule")
    public JobResponse rescheduleViaPost(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest req) {
        return reschedule(id, req);
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {}
}
