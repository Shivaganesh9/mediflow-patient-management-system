package com.mediflow.doctor.repository;

import com.mediflow.doctor.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, UUID> {
    List<DoctorAvailability> findByDoctorIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(UUID doctorId);
}
