package com.mediflow.patient.repository;

import com.mediflow.patient.entity.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PatientRepositoryTest {

    @Autowired
    private PatientRepository patientRepository;

    @Test
    void deletedPatientsAreExcludedFromActiveLookup() {
        Patient active = patientRepository.save(patient("active@example.com"));
        Patient deleted = patientRepository.save(patient("deleted@example.com"));
        deleted.setDeletedAt(Instant.now());
        patientRepository.save(deleted);

        assertTrue(patientRepository.findByPatientIdAndDeletedAtIsNull(active.getPatientId()).isPresent());
        assertFalse(patientRepository.findByPatientIdAndDeletedAtIsNull(deleted.getPatientId()).isPresent());
    }

    private Patient patient(String email) {
        Patient patient = new Patient();
        patient.setFirstName("Test");
        patient.setLastName("Patient");
        patient.setEmail(email);
        patient.setPhone("555-0100");
        return patient;
    }
}
