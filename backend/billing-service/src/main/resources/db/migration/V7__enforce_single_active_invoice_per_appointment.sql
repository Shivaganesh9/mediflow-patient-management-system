-- At most one active invoice per appointment.
--
-- Billing staff and any future automation can otherwise generate a second invoice for the same
-- appointment, which is a double-billing path that no consumer-side check can detect. Cancelled
-- invoices are excluded so a corrected invoice can still be issued after a cancellation.
--
-- Pre-flight keeps the migration non-destructive: if duplicates already exist the migration aborts
-- with an actionable message instead of deleting or merging financial records.

DO $$
DECLARE
    duplicate_groups BIGINT;
BEGIN
    SELECT count(*) INTO duplicate_groups
    FROM (
        SELECT appointment_id
        FROM invoices
        WHERE appointment_id IS NOT NULL
          AND status <> 'CANCELLED'
        GROUP BY appointment_id
        HAVING count(*) > 1
    ) duplicates;

    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION
            'Cannot enforce one active invoice per appointment: % appointment(s) already have '
            'multiple active invoices. Cancel or merge the duplicates first '
            '(see docs/runbook.md, "Invoice per appointment remediation").',
            duplicate_groups;
    END IF;
END;
$$;

CREATE UNIQUE INDEX uq_invoices_active_appointment
    ON invoices (appointment_id)
    WHERE appointment_id IS NOT NULL AND status <> 'CANCELLED';
