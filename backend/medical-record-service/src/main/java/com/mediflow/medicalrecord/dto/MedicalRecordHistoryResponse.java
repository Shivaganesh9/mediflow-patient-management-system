package com.mediflow.medicalrecord.dto;

import com.mediflow.medicalrecord.entity.MedicalRecordHistoryAction;

import java.time.Instant;
import java.util.UUID;

/** One entry of a medical record's audit trail. Contains no clinical values. */
public record MedicalRecordHistoryResponse(
        UUID historyId,
        UUID recordId,
        MedicalRecordHistoryAction action,
        String actor,
        String changedFields,
        Instant occurredAt) {
}
