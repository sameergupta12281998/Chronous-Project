package com.airtribe.chronos.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {
    List<ExecutionEntity> findByJobIdOrderByAttemptAsc(UUID jobId);

    @Query("select e from ExecutionEntity e where e.status = com.airtribe.chronos.execution.domain.ExecutionStatus.RETRY_SCHEDULED and e.nextAttemptAt <= :cutoff")
    List<ExecutionEntity> findReadyForRetry(Instant cutoff, org.springframework.data.domain.Pageable pageable);
}
