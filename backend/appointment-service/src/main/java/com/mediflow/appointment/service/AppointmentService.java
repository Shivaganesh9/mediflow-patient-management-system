package com.mediflow.appointment.service;

import com.mediflow.appointment.dto.CreateAppointmentRequest;
import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.entity.AppointmentStatus;
import com.mediflow.appointment.repository.AppointmentRepository;
import com.mediflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    @Transactional
    public Appointment createAppointment(CreateAppointmentRequest request) {
        Appointment appointment = new Appointment();
        appointment.setPatientId(request.getPatientId());
        appointment.setDoctorId(request.getDoctorId());
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        appointment.setNotes(request.getNotes());
        appointment.setReason(request.getReason());
        appointment.setStatus(AppointmentStatus.PENDING);
        return appointmentRepository.save(appointment);
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    public Appointment getAppointment(UUID appointmentId) {
        return appointmentRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + appointmentId));
    }

    @Transactional
    public Appointment cancelAppointment(UUID appointmentId) {
        Appointment appointment = getAppointment(appointmentId);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        return appointmentRepository.save(appointment);
    }
}
