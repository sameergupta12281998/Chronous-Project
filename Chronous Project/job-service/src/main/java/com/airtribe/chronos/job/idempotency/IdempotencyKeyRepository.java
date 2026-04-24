package com.airtribe.chronos.job.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyEntity.Pk> {
}
