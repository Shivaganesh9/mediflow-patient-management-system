package com.mediflow.appointment.seed;

import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.repository.AppointmentRepository;
import com.mediflow.common.seed.DevSeedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentDevDataSeederTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private AppointmentDevDataSeeder seeder;

    @Test
    void seedCreatesFourAppointments() {
        when(appointmentRepository.findByAppointmentId(any())).thenReturn(Optional.empty());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository, times(4)).save(captor.capture());

        Set<java.util.UUID> ids = captor.getAllValues().stream()
                .map(Appointment::getAppointmentId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(DevSeedData.APPOINTMENT_1_ID, DevSeedData.APPOINTMENT_2_ID,
                DevSeedData.APPOINTMENT_3_ID, DevSeedData.APPOINTMENT_4_ID), ids);

        Appointment first = captor.getAllValues().stream()
                .filter(a -> a.getAppointmentId().equals(DevSeedData.APPOINTMENT_1_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(DevSeedData.PATIENT_USER_ID, first.getPatientId());
        assertEquals(DevSeedData.DOCTOR_USER_ID, first.getDoctorId());
    }

    @Test
    void seedIsIdempotentWhenAppointmentsExist() {
        when(appointmentRepository.findByAppointmentId(any()))
                .thenAnswer(inv -> Optional.of(new Appointment()));

        seeder.seed();
        seeder.seed();

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }
}