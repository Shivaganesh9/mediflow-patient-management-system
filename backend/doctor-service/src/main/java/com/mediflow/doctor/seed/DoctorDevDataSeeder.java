package com.mediflow.doctor.seed;

import com.mediflow.common.seed.DevSeedData;
import com.mediflow.doctor.entity.Doctor;
import com.mediflow.doctor.entity.DoctorAvailability;
import com.mediflow.doctor.repository.DoctorAvailabilityRepository;
import com.mediflow.doctor.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Dev-only seeder (active under the {@code seed} profile) that creates two deterministic
 * doctors plus weekly availability for the first. The first doctor's {@code doctorId} equals
 * the auth {@code doctor} user ID so the frontend can scope lists by {@code user.id}.
 * Idempotent: existing doctor IDs and availability IDs are skipped.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
public class DoctorDevDataSeeder implements ApplicationRunner {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        createDoctor(DevSeedData.DOCTOR_USER_ID, "Dev", "Doctor", DevSeedData.DOCTOR_EMAIL,
                "+1-555-0201", "Cardiology", 12, "Cardiology", "MD, FACC", new BigDecimal("150.00"));
        createDoctor(DevSeedData.SECOND_DOCTOR_ID, "Alice", "Chen", "alice.chen@mediflow.test",
                "+1-555-0202", "Dermatology", 8, "Dermatology", "MD, FAAD", new BigDecimal("120.00"));

        createAvailability(DevSeedData.AVAILABILITY_1_ID, DevSeedData.DOCTOR_USER_ID,
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        createAvailability(DevSeedData.AVAILABILITY_2_ID, DevSeedData.DOCTOR_USER_ID,
                DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
    }

    private void createDoctor(UUID doctorId, String firstName, String lastName, String email,
                              String phone, String specialty, Integer yearsOfExperience,
                              String department, String qualification, BigDecimal fee) {
        if (doctorRepository.existsByDoctorId(doctorId)) {
            return;
        }
        Doctor doctor = new Doctor();
        doctor.setDoctorId(doctorId);
        doctor.setFirstName(firstName);
        doctor.setLastName(lastName);
        doctor.setEmail(email);
        doctor.setPhone(phone);
        doctor.setSpecialty(specialty);
        doctor.setYearsOfExperience(yearsOfExperience);
        doctor.setDepartment(department);
        doctor.setQualification(qualification);
        doctor.setConsultationFee(fee);
        doctorRepository.save(doctor);
    }

    private void createAvailability(UUID availabilityId, UUID doctorId, DayOfWeek dayOfWeek,
                                    LocalTime startTime, LocalTime endTime) {
        if (availabilityRepository.existsById(availabilityId)) {
            return;
        }
        DoctorAvailability availability = new DoctorAvailability();
        availability.setId(availabilityId);
        availability.setAvailabilityId(availabilityId);
        availability.setDoctorId(doctorId);
        availability.setDayOfWeek(dayOfWeek);
        availability.setStartTime(startTime);
        availability.setEndTime(endTime);
        availability.setActive(true);
        availabilityRepository.save(availability);
    }
}