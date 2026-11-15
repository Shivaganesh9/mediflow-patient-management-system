package com.mediflow.medicalrecord.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.logging.CorrelationContext;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.event.outbox.OutboxEvent;
import com.mediflow.medicalrecord.event.outbox.OutboxEventRepository;
import com.mediflow.medicalrecord.event.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds medical-record domain events and stores them in the transactional outbox.
 *
 * <p>Publishing to Kafka is deferred to {@code OutboxRelay} so the record write and the event are
 * atomic. Event payloads can contain clinical text; they are stored durably but never logged.</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalRecordEventPublisher {

    public static final String TOPIC_MEDICAL_RECORD_CREATED = "medical-record.created.v1";
    public static final String TOPIC_MEDICAL_RECORD_UPDATED = "medical-record.updated.v1";

    private static final String PRODUCER_SERVICE = "medical-record-service";
    private static final String EVENT_TYPE_MEDICAL_RECORD_CREATED = "MEDICAL_RECORD_CREATED";
    private static final String EVENT_TYPE_MEDICAL_RECORD_UPDATED = "MEDICAL_RECORD_UPDATED";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

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
                .correlationId(CorrelationContext.currentUuid())
                .aggregateId(medicalRecord.getRecordId())
                .payload(payload)
                .build();

        enqueue(topic, event);
    }

    private void enqueue(String topic, MedicalRecordEvent event) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventId(event.getEventId());
        outboxEvent.setAggregateId(event.getAggregateId());
        outboxEvent.setTopic(topic);
        outboxEvent.setEventType(event.getEventType());
        outboxEvent.setEventClass(event.getClass().getName());
        outboxEvent.setPayload(serialize(event));
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttempts(0);
        outboxEvent.setNextAttemptAt(Instant.now());
        outboxRepository.save(outboxEvent);
    }

    private String serialize(MedicalRecordEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize medical record event", ex);
        }
    }
}
