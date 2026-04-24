package com.airtribe.chronos.scheduler.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<ScheduleEntity, UUID> {
    Optional<ScheduleEntity> findByJobId(UUID jobId);

    @Query("select s from ScheduleEntity s where s.active = true and s.nextRunAt <= :cutoff order by s.nextRunAt asc")
    List<ScheduleEntity> findDue(Instant cutoff, org.springframework.data.domain.Pageable pageable);
}
