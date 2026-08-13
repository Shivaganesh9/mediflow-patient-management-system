package com.mediflow.appointment.mapper;

import com.mediflow.appointment.dto.AppointmentResponse;
import com.mediflow.appointment.entity.Appointment;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {

    public AppointmentResponse toResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .appointmentId(appointment.getAppointmentId())
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus())
                .notes(appointment.getNotes())
                .reason(appointment.getReason())
                .appointmentType(appointment.getAppointmentType())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .version(appointment.getVersion())
                .build();
    }
}
