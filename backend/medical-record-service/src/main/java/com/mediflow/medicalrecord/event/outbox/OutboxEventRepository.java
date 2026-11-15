package com.mediflow.medicalrecord.event.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Locks the oldest due rows so concurrent relay instances cannot publish in parallel. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEvent o "
            + "where o.status = com.mediflow.medicalrecord.event.outbox.OutboxStatus.PENDING "
            + "and o.nextAttemptAt <= :now order by o.createdAt asc")
    List<OutboxEvent> lockPendingBatch(@Param("now") Instant now, Pageable pageable);

    long countByStatus(OutboxStatus status);
}
