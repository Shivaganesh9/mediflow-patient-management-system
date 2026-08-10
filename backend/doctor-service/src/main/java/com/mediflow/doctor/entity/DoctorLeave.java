package com.mediflow.doctor.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "doctor_leaves", indexes = @Index(name = "idx_doctor_leaves_doctor_dates", columnList = "doctor_id,start_date,end_date"))
@Getter
@Setter
@NoArgsConstructor
public class DoctorLeave extends BaseEntity {
    @Column(name = "leave_id", nullable = false, unique = true, updatable = false)
    private UUID leaveId = UUID.randomUUID();
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Column(length = 500)
    private String reason;
}
