package com.mediflow.appointment.repository;

import com.mediflow.appointment.entity.AppointmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentHistoryRepository extends JpaRepository<AppointmentHistory, UUID> {

    List<AppointmentHistory> findByAppointmentIdOrderByOccurredAtAscIdAsc(UUID appointmentId);
}
