package com.mediflow.medicalrecord.event;

import com.mediflow.medicalrecord.entity.MedicalRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MedicalRecordEventPublisher {

    public static final String TOPIC_MEDICAL_RECORD_CREATED = "medical-record.created.v1";
    public static final String TOPIC_MEDICAL_RECORD_UPDATED = "medical-record.updated.v1";

    private static final String PRODUCER_SERVICE = "medical-record-service";
    private static final String EVENT_TYPE_MEDICAL_RECORD_CREATED = "MEDICAL_RECORD_CREATED";
    private static final String EVENT_TYPE_MEDICAL_RECORD_UPDATED = "MEDICAL_RECORD_UPDATED";

    private final KafkaTemplate<String, MedicalRecordEvent> kafkaTemplate;

    public void publishMedicalRecordCreated(MedicalRecord medicalRecord) {
        publish(TOPIC_MEDICAL_RECORD_CREATED, EVENT_TYPE_MEDICAL_RECORD_CREATED, medicalRecord);
    }

    public void publishMedicalRecordUpdated(MedicalRecord medicalRecord) {
        publish(TOPIC_MEDICAL_RECORD_UPDATED, EVENT_TYPE_MEDICAL_RECORD_UPDATED, medicalRecord);
    }

    private void publish(String topic, String eventType, MedicalRecord medicalRecord) {
        MedicalRecordEvent.Payload payload = MedicalRecordEvent.Payload.builder()
                .recordId(medicalRecord.getRecordId())
                .patientId(medicalRecord.getPatientId())
                .appointmentId(medicalRecord.getAppointmentId())
                .doctorId(medicalRecord.getDoctorId())
                .encounterDate(medicalRecord.getEncounterDate())
                .recordType(medicalRecord.getRecordType())
                .chiefComplaint(medicalRecord.getChiefComplaint())
                .diagnosis(medicalRecord.getDiagnosis())
                .treatmentPlan(medicalRecord.getTreatmentPlan())
                .notes(medicalRecord.getNotes())
                .version(medicalRecord.getVersion())
                .build();

        MedicalRecordEvent event = MedicalRecordEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(1)
                .eventType(eventType)
                .occurredAt(Instant.now())
                .producerService(PRODUCER_SERVICE)
                .aggregateId(medicalRecord.getRecordId())
                .payload(payload)
                .build();

        kafkaTemplate.send(topic, medicalRecord.getRecordId().toString(), event);
    }
}
