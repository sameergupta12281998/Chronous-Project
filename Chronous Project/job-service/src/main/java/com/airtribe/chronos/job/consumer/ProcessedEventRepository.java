package com.airtribe.chronos.job.consumer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventId> {
}
