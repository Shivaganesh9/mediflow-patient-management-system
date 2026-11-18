package com.mediflow.doctor.repository;

import com.mediflow.doctor.entity.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, UUID> {
    List<DoctorLeave> findByDoctorIdAndDeletedAtIsNullOrderByStartDateAsc(UUID doctorId);

    Optional<DoctorLeave> findByLeaveIdAndDoctorIdAndDeletedAtIsNull(UUID leaveId, UUID doctorId);

    List<DoctorLeave> findByDoctorIdAndDeletedAtIsNull(UUID doctorId);

    boolean existsByDoctorIdAndDeletedAtIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID doctorId, LocalDate rangeEnd, LocalDate rangeStart);

    boolean existsByDoctorIdAndDeletedAtIsNullAndLeaveIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID doctorId, UUID leaveId, LocalDate rangeEnd, LocalDate rangeStart);
}
