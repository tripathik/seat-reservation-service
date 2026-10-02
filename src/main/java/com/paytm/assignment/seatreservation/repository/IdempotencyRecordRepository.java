package com.paytm.assignment.seatreservation.repository;

import com.paytm.assignment.seatreservation.entity.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, UUID> {
    @Modifying
    @Query(
            value = """
                INSERT INTO idempotency_records
                    (id, user_id, idempotency_key, request_fingerprint, created_at)
                VALUES
                    (gen_random_uuid(), :userId, :idempotencyKey, :requestFingerprint, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """,
            nativeQuery = true
    )
    int createIfAbsent(
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestFingerprint") String requestFingerprint
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT i
       FROM IdempotencyRecord i
       WHERE i.userId = :userId
         AND i.idempotencyKey = :idempotencyKey
       """)
    Optional<IdempotencyRecord> findForUpdate(
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}