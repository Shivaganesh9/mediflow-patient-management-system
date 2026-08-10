package com.mediflow.doctor.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "doctor_availability", indexes = @Index(name = "idx_availability_doctor_day", columnList = "doctor_id,day_of_week"))
@Getter
@Setter
@NoArgsConstructor
public class DoctorAvailability extends BaseEntity {
    @Column(name = "availability_id", nullable = false, unique = true, updatable = false)
    private UUID availabilityId = UUID.randomUUID();
    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(nullable = false)
    private boolean active = true;
}
