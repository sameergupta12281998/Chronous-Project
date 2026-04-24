package com.airtribe.chronos.execution.runner;

import com.airtribe.chronos.execution.domain.ExecutionEntity;
import com.airtribe.chronos.execution.domain.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final ExecutionRepository repository;
    private final ExecutionRunner runner;

    public RetryScheduler(ExecutionRepository repository, ExecutionRunner runner) {
        this.repository = repository;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${chronos.execution.retry-poll-ms:1000}")
    @Transactional
    public void runDueRetries() {
        List<ExecutionEntity> ready = repository.findReadyForRetry(Instant.now(), PageRequest.of(0, 50));
        for (ExecutionEntity prev : ready) {
            log.info("Retrying job {} attempt {} of {}", prev.getJobId(), prev.getAttempt() + 1, prev.getMaxAttempts());
            runner.runAttempt(prev.getJobId(), prev.getOwnerId(), prev.getTaskType(), prev.getPayload(),
                    prev.getAttempt() + 1, prev.getMaxAttempts(), prev.getCorrelationId());
            // Mark previous attempt as no longer eligible by transitioning to FAILED
            prev.fail(prev.getError());
            repository.save(prev);
        }
    }
}
