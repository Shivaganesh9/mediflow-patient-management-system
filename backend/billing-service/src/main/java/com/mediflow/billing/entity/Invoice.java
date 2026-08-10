package com.mediflow.billing.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID invoiceId = UUID.randomUUID();

    @Column(nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String status = "PENDING";
}
