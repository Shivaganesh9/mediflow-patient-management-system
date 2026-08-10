package com.mediflow.appointment.repository;

import com.mediflow.appointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.appointmentId = :appointmentId")
    Optional<Appointment> findByAppointmentIdForUpdate(@Param("appointmentId") UUID appointmentId);

    @Query("select count(a) > 0 from Appointment a where (a.patientId = :patientId or a.doctorId = :doctorId) "
        + "and a.status not in ('CANCELLED', 'COMPLETED', 'NO_SHOW') "
        + "and a.startTime < :endTime and a.endTime > :startTime")
    boolean hasOverlappingAppointment(@Param("patientId") UUID patientId, @Param("doctorId") UUID doctorId,
                      @Param("startTime") java.time.LocalDateTime startTime,
                      @Param("endTime") java.time.LocalDateTime endTime);

    @Query("select count(a) > 0 from Appointment a where a.appointmentId <> :appointmentId "
        + "and (a.patientId = :patientId or a.doctorId = :doctorId) "
        + "and a.status not in ('CANCELLED', 'COMPLETED', 'NO_SHOW') "
        + "and a.startTime < :endTime and a.endTime > :startTime")
    boolean hasOverlappingAppointmentForUpdate(@Param("appointmentId") UUID appointmentId,
                           @Param("patientId") UUID patientId, @Param("doctorId") UUID doctorId,
                           @Param("startTime") java.time.LocalDateTime startTime,
                           @Param("endTime") java.time.LocalDateTime endTime);

    @Query("select a from Appointment a where (:status is null or a.status = :status) "
        + "and (:patientId is null or a.patientId = :patientId) "
        + "and (:doctorId is null or a.doctorId = :doctorId) "
        + "and (:fromTime is null or a.startTime >= :fromTime) "
        + "and (:toTime is null or a.endTime <= :toTime)")
    Page<Appointment> search(@Param("status") AppointmentStatus status, @Param("patientId") UUID patientId,
                 @Param("doctorId") UUID doctorId, @Param("fromTime") java.time.LocalDateTime fromTime,
                 @Param("toTime") java.time.LocalDateTime toTime, Pageable pageable);
}
