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
@Table(name = "payment_facts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentFact extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID sourceEventId;

    private UUID invoiceId;

    private UUID patientId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String currency;

    private String method;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant paidAt;
}