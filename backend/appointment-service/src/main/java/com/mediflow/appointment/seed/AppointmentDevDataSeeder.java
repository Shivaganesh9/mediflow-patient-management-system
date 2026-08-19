package com.mediflow.appointment.seed;

import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.entity.AppointmentStatus;
import com.mediflow.appointment.repository.AppointmentRepository;
import com.mediflow.common.seed.DevSeedData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dev-only seeder (active under the {@code seed} profile) that creates four deterministic
 * appointments. The first two belong to the seeded {@code patient} user and the seeded
 * {@code doctor} user, so the frontend (which scopes by {@code user.id}) shows them.
 * Idempotent: existing appointment IDs are skipped.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
public class AppointmentDevDataSeeder implements ApplicationRunner {

    private final AppointmentRepository appointmentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        LocalDateTime now = LocalDateTime.now();
        createAppointment(DevSeedData.APPOINTMENT_1_ID, DevSeedData.PATIENT_USER_ID,
                DevSeedData.DOCTOR_USER_ID, now.plusDays(1).withHour(10).withMinute(0),
                now.plusDays(1).withHour(10).withMinute(30), AppointmentStatus.SCHEDULED,
                "Routine check-up", "GENERAL");
        createAppointment(DevSeedData.APPOINTMENT_2_ID, DevSeedData.PATIENT_USER_ID,
                DevSeedData.DOCTOR_USER_ID, now.plusDays(3).withHour(14).withMinute(0),
                now.plusDays(3).withHour(14).withMinute(30), AppointmentStatus.CONFIRMED,
                "Follow-up consultation", "FOLLOW_UP");
        createAppointment(DevSeedData.APPOINTMENT_3_ID, DevSeedData.SECOND_PATIENT_ID,
                DevSeedData.DOCTOR_USER_ID, now.minusDays(7).withHour(9).withMinute(0),
                now.minusDays(7).withHour(9).withMinute(30), AppointmentStatus.COMPLETED,
                "Dermatology consultation", "CONSULTATION");
        createAppointment(DevSeedData.APPOINTMENT_4_ID, DevSeedData.SECOND_PATIENT_ID,
                DevSeedData.SECOND_DOCTOR_ID, now.plusDays(5).withHour(11).withMinute(0),
                now.plusDays(5).withHour(11).withMinute(30), AppointmentStatus.SCHEDULED,
                "Skin check", "GENERAL");
    }

    private void createAppointment(UUID appointmentId, UUID patientId, UUID doctorId,
                                   LocalDateTime startTime, LocalDateTime endTime,
                                   AppointmentStatus status, String reason, String appointmentType) {
        if (appointmentRepository.findByAppointmentId(appointmentId).isPresent()) {
            return;
        }
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setPatientId(patientId);
        appointment.setDoctorId(doctorId);
        appointment.setStartTime(startTime);
        appointment.setEndTime(endTime);
        appointment.setStatus(status);
        appointment.setReason(reason);
        appointment.setAppointmentType(appointmentType);
        appointmentRepository.save(appointment);
    }
}