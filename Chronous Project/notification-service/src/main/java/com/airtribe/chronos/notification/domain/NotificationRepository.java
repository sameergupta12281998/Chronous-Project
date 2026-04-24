package com.airtribe.chronos.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    Page<NotificationEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);
}
