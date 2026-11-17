package com.mediflow.billing.repository;

import com.mediflow.billing.entity.PaymentIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyRecord, UUID> {

    Optional<PaymentIdempotencyRecord> findByRequesterAndIdempotencyKey(String requester, String idempotencyKey);

    /** Bulk prune used by the retention job; returns the number of removed claims. */
    @Modifying
    @Query("delete from PaymentIdempotencyRecord r where r.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
