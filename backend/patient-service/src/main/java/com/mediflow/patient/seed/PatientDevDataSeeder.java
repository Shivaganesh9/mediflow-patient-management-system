package com.mediflow.patient.seed;

import com.mediflow.common.seed.DevSeedData;
import com.mediflow.patient.entity.Patient;
import com.mediflow.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dev-only seeder (active under the {@code seed} profile) that creates two deterministic
 * patients. The first patient's {@code patientId} equals the auth {@code patient} user ID so
 * the frontend can scope lists by {@code user.id}. Idempotent: existing patient IDs are skipped.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
public class PatientDevDataSeeder implements ApplicationRunner {

    private final PatientRepository patientRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        createPatient(DevSeedData.PATIENT_USER_ID, "Dev", "Patient", DevSeedData.PATIENT_EMAIL,
                "+1-555-0101", LocalDate.of(1988, 4, 12), "10 Main Street, Springfield");
        createPatient(DevSeedData.SECOND_PATIENT_ID, "Jane", "Smith", "jane.smith@mediflow.test",
                "+1-555-0102", LocalDate.of(1992, 9, 3), "22 Oak Avenue, Shelbyville");
    }

    private void createPatient(UUID patientId, String firstName, String lastName, String email,
                               String phone, LocalDate dateOfBirth, String address) {
        if (patientRepository.existsByPatientId(patientId)) {
            return;
        }
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setEmail(email);
        patient.setPhone(phone);
        patient.setDateOfBirth(dateOfBirth);
        patient.setAddress(address);
        patient.setEmergencyContactName("Emergency Contact");
        patient.setEmergencyContactPhone("555-0199");
        patient.setInsuranceProvider("MediFlow Health");
        patient.setInsuranceMemberId("MF-" + patientId.toString().substring(0, 8).toUpperCase());
        patientRepository.save(patient);
    }
}