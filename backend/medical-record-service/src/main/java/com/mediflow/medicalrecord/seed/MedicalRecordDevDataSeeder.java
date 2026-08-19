package com.mediflow.medicalrecord.seed;

import com.mediflow.common.seed.DevSeedData;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.entity.MedicalRecordType;
import com.mediflow.medicalrecord.repository.MedicalRecordRepository;
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
 * medical records referencing the seeded patient/doctor/appointment IDs. Idempotent:
 * existing record IDs are skipped.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
public class MedicalRecordDevDataSeeder implements ApplicationRunner {

    private final MedicalRecordRepository medicalRecordRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        createRecord(DevSeedData.MEDICAL_RECORD_1_ID, DevSeedData.PATIENT_USER_ID,
                DevSeedData.DOCTOR_USER_ID, DevSeedData.APPOINTMENT_1_ID,
                LocalDate.now().minusDays(2), MedicalRecordType.CONSULTATION,
                "Annual check-up", "Healthy", "No treatment required.");
        createRecord(DevSeedData.MEDICAL_RECORD_2_ID, DevSeedData.SECOND_PATIENT_ID,
                DevSeedData.DOCTOR_USER_ID, DevSeedData.APPOINTMENT_3_ID,
                LocalDate.now().minusDays(7), MedicalRecordType.FOLLOW_UP,
                "Eczema flare-up", "Mild eczema", "Topical corticosteroid, follow up in 2 weeks.");
    }

    private void createRecord(UUID recordId, UUID patientId, UUID doctorId, UUID appointmentId,
                              LocalDate encounterDate, MedicalRecordType recordType,
                              String chiefComplaint, String diagnosis, String notes) {
        if (medicalRecordRepository.findByRecordId(recordId).isPresent()) {
            return;
        }
        MedicalRecord record = new MedicalRecord();
        record.setRecordId(recordId);
        record.setPatientId(patientId);
        record.setDoctorId(doctorId);
        record.setAppointmentId(appointmentId);
        record.setEncounterDate(encounterDate);
        record.setRecordType(recordType);
        record.setChiefComplaint(chiefComplaint);
        record.setDiagnosis(diagnosis);
        record.setTreatmentPlan(notes);
        record.setNotes(notes);
        medicalRecordRepository.save(record);
    }
}