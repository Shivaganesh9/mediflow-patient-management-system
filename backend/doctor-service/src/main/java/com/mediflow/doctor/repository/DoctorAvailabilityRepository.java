package com.mediflow.doctor.repository;

import com.mediflow.doctor.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, UUID> {
    List<DoctorAvailability> findByDoctorIdAndActiveTrueAndDeletedAtIsNullOrderByDayOfWeekAscStartTimeAsc(UUID doctorId);

    List<DoctorAvailability> findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(UUID doctorId, DayOfWeek dayOfWeek);

    Optional<DoctorAvailability> findByAvailabilityIdAndDoctorIdAndDeletedAtIsNull(UUID availabilityId, UUID doctorId);
}
