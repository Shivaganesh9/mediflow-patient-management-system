package com.mediflow.medicalrecord.entity;

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
@Table(name = "medical_records")
@Getter
@Setter
@NoArgsConstructor
public class MedicalRecord extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID recordId = UUID.randomUUID();

    @Column(nullable = false)
    private UUID patientId;

    private UUID appointmentId;

    @Column(nullable = false)
    private LocalDate encounterDate;

    @Column(nullable = false, length = 500)
    private String chiefComplaint;

    @Column(nullable = false, length = 10000)
    private String notes;
}
