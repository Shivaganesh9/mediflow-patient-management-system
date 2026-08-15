package com.mediflow.analytics.repository;

import com.mediflow.analytics.entity.PaymentFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentFactRepository extends JpaRepository<PaymentFact, UUID> {

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM payment_facts " +
            "WHERE status = 'COMPLETED' AND paid_at >= :from AND paid_at < :toExclusive",
            nativeQuery = true)
    BigDecimal sumCollectedBetween(@Param("from") Instant from, @Param("toExclusive") Instant toExclusive);

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM payment_facts " +
            "WHERE status = 'REFUNDED' AND paid_at >= :from AND paid_at < :toExclusive",
            nativeQuery = true)
    BigDecimal sumRefundedBetween(@Param("from") Instant from, @Param("toExclusive") Instant toExclusive);

    @Query(value = "SELECT date_trunc('month', paid_at), status, COALESCE(SUM(amount), 0) FROM payment_facts " +
            "WHERE paid_at >= :from AND paid_at < :toExclusive GROUP BY 1, 2 ORDER BY 1",
            nativeQuery = true)
    List<Object[]> monthlyPaymentsBetween(@Param("from") Instant from, @Param("toExclusive") Instant toExclusive);
}