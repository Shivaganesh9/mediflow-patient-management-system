-- The role catalogue is referenced by the administrative role-assignment API and by
-- documentation (RBAC matrix). V1 only seeded PATIENT/DOCTOR/ADMIN; the remaining
-- assignable roles are added here so every environment has the full catalogue.
INSERT INTO roles (id, name, description, created_at, updated_at, created_by, updated_by, deleted_at) VALUES
('44444444-4444-4444-4444-444444444444','NURSE','Nurse role',NOW(),NOW(),'system','system',NULL),
('55555555-5555-5555-5555-555555555555','BILLING_STAFF','Billing staff role',NOW(),NOW(),'system','system',NULL),
('66666666-6666-6666-6666-666666666666','SUPPORT','Support role',NOW(),NOW(),'system','system',NULL)
ON CONFLICT (name) DO NOTHING;
