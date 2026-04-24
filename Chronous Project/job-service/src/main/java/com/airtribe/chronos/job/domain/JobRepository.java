package com.airtribe.chronos.job.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    Page<JobEntity> findByOwnerId(UUID ownerId, Pageable pageable);
    Page<JobEntity> findByOwnerIdAndStatus(UUID ownerId, JobStatus status, Pageable pageable);
}
