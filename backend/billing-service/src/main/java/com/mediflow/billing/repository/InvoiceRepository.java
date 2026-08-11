package com.mediflow.billing.repository;

import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceId(UUID invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.invoiceId = :invoiceId")
    Optional<Invoice> findByInvoiceIdForUpdate(@Param("invoiceId") UUID invoiceId);

    @Query("select i from Invoice i "
            + "where (:status is null or i.status = :status) "
            + "and (:patientId is null or i.patientId = :patientId) "
            + "and (:appointmentId is null or i.appointmentId = :appointmentId)")
    Page<Invoice> search(@Param("status") InvoiceStatus status, @Param("patientId") UUID patientId,
                         @Param("appointmentId") UUID appointmentId, Pageable pageable);
}
