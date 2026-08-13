package com.mediflow.medicalrecord.event;

import com.mediflow.medicalrecord.entity.MedicalRecordType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordEvent {

    private UUID eventId;
    private int eventVersion;
    private String eventType;
    private Instant occurredAt;
    private String producerService;
    private UUID correlationId;
    private UUID causationId;
    private UUID aggregateId;
    private Payload payload;
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private UUID recordId;
        private UUID patientId;
        private UUID appointmentId;
        private UUID doctorId;
        private LocalDate encounterDate;
        private MedicalRecordType recordType;
        private String chiefComplaint;
        private String diagnosis;
        private String treatmentPlan;
        private String notes;
        private Long version;
    }
}
