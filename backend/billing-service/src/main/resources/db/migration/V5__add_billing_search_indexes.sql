-- Composite indexes for invoice listing and payment-history queries.
CREATE INDEX idx_invoices_patient_issued ON invoices (patient_id, issued_at);
CREATE INDEX idx_invoices_status_issued ON invoices (status, issued_at);
CREATE INDEX idx_payments_invoice_status ON payments (invoice_id, status);
