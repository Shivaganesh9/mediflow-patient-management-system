package com.mediflow.analytics.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_facts")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceFact extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID invoiceId;

    @Column(nullable = false)
    private UUID sourceEventId;

    private UUID patientId;

    private UUID appointmentId;

    private String invoiceNumber;

    private String status;

    private String currency;

    private BigDecimal subtotal;

    private BigDecimal taxAmount;

    private BigDecimal discount;

    private BigDecimal totalAmount;

    private BigDecimal paidAmount;

    @Column(nullable = false)
    private Instant invoiceDate;

    private Long version;
}