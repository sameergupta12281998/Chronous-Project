package com.airtribe.chronos.job.idempotency;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps a client-supplied {@code Idempotency-Key} (scoped to an owner) to the job that
 * was originally created for that key. A duplicate POST with the same key returns the
 * original job instead of creating a new one — protecting against retried HTTP requests
 * that succeeded server-side but never reached the client.
 */
@Entity
@Table(name = "job_idempotency_keys")
@IdClass(IdempotencyKeyEntity.Pk.class)
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Id
    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {}

    public IdempotencyKeyEntity(String idempotencyKey, UUID ownerId, UUID jobId) {
        this.idempotencyKey = idempotencyKey;
        this.ownerId = ownerId;
        this.jobId = jobId;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getJobId() { return jobId; }
    public Instant getCreatedAt() { return createdAt; }

    public static class Pk implements Serializable {
        private String idempotencyKey;
        private UUID ownerId;
        public Pk() {}
        public Pk(String idempotencyKey, UUID ownerId) {
            this.idempotencyKey = idempotencyKey;
            this.ownerId = ownerId;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(idempotencyKey, pk.idempotencyKey) && Objects.equals(ownerId, pk.ownerId);
        }
        @Override public int hashCode() { return Objects.hash(idempotencyKey, ownerId); }
    }
}
