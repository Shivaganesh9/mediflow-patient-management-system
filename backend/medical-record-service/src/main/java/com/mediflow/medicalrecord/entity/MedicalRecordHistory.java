package com.mediflow.medicalrecord.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit entry for a medical record.
 *
 * <p>Only metadata is stored (who changed which fields and when). Clinical values are deliberately
 * not copied into this table: the audit trail proves what changed without creating a second copy of
 * sensitive patient data that would need its own retention and access controls.</p>
 */
@Entity
@Table(name = "medical_record_history",
        indexes = @Index(name = "idx_medical_record_history_record",
                columnList = "record_id,occurred_at"))
@Getter
@Setter
@NoArgsConstructor
public class MedicalRecordHistory extends BaseEntity {

    @Column(name = "history_id", nullable = false, unique = true, updatable = false)
    private UUID historyId = UUID.randomUUID();

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private MedicalRecordHistoryAction action;

    @Column(name = "actor", updatable = false, length = 255)
    private String actor;

    /** Comma-separated field names that changed; never the field values. */
    @Column(name = "changed_fields", updatable = false, length = 500)
    private String changedFields;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
