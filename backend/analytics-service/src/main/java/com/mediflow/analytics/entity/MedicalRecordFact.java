package com.mediflow.analytics.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "medical_record_facts")
@Getter
@Setter
@NoArgsConstructor
public class MedicalRecordFact extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID recordId;

    @Column(nullable = false)
    private UUID sourceEventId;

    @Column(nullable = false)
    private UUID patientId;

    private UUID doctorId;

    @Column(nullable = false)
    private LocalDate encounterDate;

    private String recordType;
}