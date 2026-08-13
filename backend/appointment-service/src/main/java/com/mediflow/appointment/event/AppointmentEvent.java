package com.mediflow.appointment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEvent {

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
        private UUID appointmentId;
        private UUID patientId;
        private UUID doctorId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
        private String appointmentType;
        private String reason;
        private Long version;
    }
}
