package com.mediflow.doctor.repository;

import com.mediflow.doctor.entity.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, UUID> {
    List<DoctorLeave> findByDoctorIdOrderByStartDateAsc(UUID doctorId);
}
