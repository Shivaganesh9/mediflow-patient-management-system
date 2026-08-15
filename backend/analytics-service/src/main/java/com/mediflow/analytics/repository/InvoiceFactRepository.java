package com.mediflow.analytics.repository;

import com.mediflow.analytics.entity.InvoiceFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceFactRepository extends JpaRepository<InvoiceFact, UUID> {

    Optional<InvoiceFact> findByInvoiceId(UUID invoiceId);

    @Query(value = "SELECT COALESCE(SUM(total_amount), 0) FROM invoice_facts " +
            "WHERE status <> 'CANCELLED' AND invoice_date >= :from AND invoice_date < :toExclusive",
            nativeQuery = true)
    BigDecimal sumInvoicedBetween(@Param("from") Instant from, @Param("toExclusive") Instant toExclusive);

    @Query(value = "SELECT date_trunc('month', invoice_date), COALESCE(SUM(total_amount), 0) FROM invoice_facts " +
            "WHERE status <> 'CANCELLED' AND invoice_date >= :from AND invoice_date < :toExclusive " +
            "GROUP BY 1 ORDER BY 1",
            nativeQuery = true)
    List<Object[]> monthlyInvoicedBetween(@Param("from") Instant from, @Param("toExclusive") Instant toExclusive);
}