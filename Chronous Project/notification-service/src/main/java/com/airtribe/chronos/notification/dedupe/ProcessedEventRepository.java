package com.airtribe.chronos.notification.dedupe;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.Pk> {
}
