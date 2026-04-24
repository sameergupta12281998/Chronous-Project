package com.airtribe.chronos.job.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    @Query("select o from OutboxEntity o where o.sentAt is null order by o.createdAt asc")
    List<OutboxEntity> findUnsent(org.springframework.data.domain.Pageable pageable);
}
