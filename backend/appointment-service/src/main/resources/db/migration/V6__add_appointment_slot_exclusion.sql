-- Database-enforced protection against impossible appointments: a doctor cannot be in two places
-- at once, and neither can a patient.
--
-- The appointment service already checks for overlaps before writing, but that check is a
-- read-then-write race: two concurrent bookings for the same slot both see a free calendar and
-- both commit. The exclusion constraints below make PostgreSQL the final authority, so the second
-- writer blocks on the index entry and then fails with SQLSTATE 23P01 (exclusion_violation), which
-- the service translates into HTTP 409.
--
-- Reserving statuses: SCHEDULED, CONFIRMED and IN_PROGRESS hold the slot. CANCELLED, COMPLETED and
-- NO_SHOW release it, which is what the application-level check already assumed. Bounds are
-- '[)' so back-to-back appointments (10:00-11:00 and 11:00-12:00) do not conflict.
--
-- btree_gist is required because each constraint mixes an equality (doctor_id / patient_id) and a
-- range overlap (tsrange) operator; it ships with PostgreSQL contrib and the compose/test images run
-- as a superuser.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Pre-flight: never destroy or silently rewrite business data. If the existing calendar already
-- contains conflicts, abort the migration with an actionable message and leave the data untouched
-- (the whole migration runs in one transaction, so nothing is half-applied). Remediation steps are
-- documented in docs/runbook.md.
DO $$
DECLARE
    invalid_ranges BIGINT;
    overlapping_doctor_pairs BIGINT;
    overlapping_patient_pairs BIGINT;
BEGIN
    SELECT count(*) INTO invalid_ranges
    FROM appointments
    WHERE end_time <= start_time;

    IF invalid_ranges > 0 THEN
        RAISE EXCEPTION
            'Cannot add appointment slot constraints: % appointment(s) have end_time <= start_time. '
            'Correct them first (see docs/runbook.md, "Appointment slot constraint remediation").',
            invalid_ranges;
    END IF;

    SELECT count(*) INTO overlapping_doctor_pairs
    FROM appointments a
    JOIN appointments b
      ON a.doctor_id = b.doctor_id
     AND a.id < b.id
    WHERE a.status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS')
      AND b.status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS')
      AND a.deleted_at IS NULL
      AND b.deleted_at IS NULL
      AND tsrange(a.start_time, a.end_time, '[)') && tsrange(b.start_time, b.end_time, '[)');

    IF overlapping_doctor_pairs > 0 THEN
        RAISE EXCEPTION
            'Cannot add appointment slot exclusion constraint: % pair(s) of active appointments '
            'overlap for the same doctor. Reschedule or cancel them first '
            '(see docs/runbook.md, "Appointment slot constraint remediation").',
            overlapping_doctor_pairs;
    END IF;

    SELECT count(*) INTO overlapping_patient_pairs
    FROM appointments a
    JOIN appointments b
      ON a.patient_id = b.patient_id
     AND a.id < b.id
    WHERE a.status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS')
      AND b.status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS')
      AND a.deleted_at IS NULL
      AND b.deleted_at IS NULL
      AND tsrange(a.start_time, a.end_time, '[)') && tsrange(b.start_time, b.end_time, '[)');

    IF overlapping_patient_pairs > 0 THEN
        RAISE EXCEPTION
            'Cannot add appointment slot exclusion constraint: % pair(s) of active appointments '
            'overlap for the same patient. Reschedule or cancel them first '
            '(see docs/runbook.md, "Appointment slot constraint remediation").',
            overlapping_patient_pairs;
    END IF;
END;
$$;

ALTER TABLE appointments
    ADD CONSTRAINT ck_appointments_time_range CHECK (end_time > start_time);

ALTER TABLE appointments
    ADD CONSTRAINT ex_appointments_doctor_slot
    EXCLUDE USING gist (
        doctor_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS') AND deleted_at IS NULL);

ALTER TABLE appointments
    ADD CONSTRAINT ex_appointments_patient_slot
    EXCLUDE USING gist (
        patient_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS') AND deleted_at IS NULL);
