package com.mediflow.analytics.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointment_facts")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentFact extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID appointmentId;

    @Column(nullable = false)
    private UUID sourceEventId;

    @Column(nullable = false)
    private UUID patientId;

    private UUID doctorId;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    private LocalDateTime startTime;

    private String status;

    private String appointmentType;

    private Long version;
}