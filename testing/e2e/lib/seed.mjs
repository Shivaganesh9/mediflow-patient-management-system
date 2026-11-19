/**
 * Deterministic dev accounts and IDs from
 * `backend/common-library/src/main/java/com/mediflow/common/seed/DevSeedData.java`.
 *
 * The E2E run starts every service with `SPRING_PROFILES_ACTIVE=seed`, so these rows exist in a
 * freshly created database. Keep this file in sync when the seed changes.
 */
export const USERS = {
  admin: { username: 'admin', password: 'Admin@123', id: '00000000-0000-0000-0000-000000000001' },
  doctor: { username: 'doctor', password: 'Doctor@123', id: '00000000-0000-0000-0000-000000000002' },
  patient: { username: 'patient', password: 'Patient@123', id: '00000000-0000-0000-0000-000000000003' },
  nurse: { username: 'nurse', password: 'Nurse@123', id: '00000000-0000-0000-0000-000000000006' },
  billing: { username: 'billing', password: 'Billing@123', id: '00000000-0000-0000-0000-000000000007' },
  support: { username: 'support', password: 'Support@123', id: '00000000-0000-0000-0000-000000000008' },
};

/** IDs referenced by the journeys. */
export const SEED = {
  secondDoctorId: '00000000-0000-0000-0000-000000000004',
  secondPatientId: '00000000-0000-0000-0000-000000000005',
  firstAppointmentId: '00000000-0000-0000-0000-000000000010',
  otherPatientAppointmentId: '00000000-0000-0000-0000-000000000012',
  paidInvoiceId: '00000000-0000-0000-0000-000000000030',
  pendingInvoiceId: '00000000-0000-0000-0000-000000000031',
};
