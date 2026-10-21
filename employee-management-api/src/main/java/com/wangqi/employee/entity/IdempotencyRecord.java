package com.wangqi.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Stores the outcome of a request identified by an {@code Idempotency-Key}
 * header, so a client retry returns the original result instead of creating a
 * duplicate. The key itself is the primary key: the database, not application
 * code, is the final guard against concurrent duplicates.
 *
 * @author wangqi
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String key;

    /**
     * SHA-256 of the request payload. A replay with the same key but a
     * different body is rejected instead of silently returning stale data.
     */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_body", nullable = false, length = 2000)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
