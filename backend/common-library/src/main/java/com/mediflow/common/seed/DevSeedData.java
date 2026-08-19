package com.mediflow.common.seed;

import java.util.UUID;

/**
 * Deterministic IDs and credentials used by the dev-only {@code seed} profile seeders.
 *
 * <p>These values keep cross-service references consistent: the auth user IDs for the
 * seeded {@code doctor} and {@code patient} accounts equal the corresponding
 * {@code doctors.doctorId} / {@code patients.patientId} business keys, which is what the
 * frontend relies on when it scopes lists by {@code user.id}. Never use these in
 * production data.</p>
 */
public final class DevSeedData {

    private DevSeedData() {
    }

    public static final UUID ADMIN_USER_ID = uuid("00000000-0000-0000-0000-000000000001");
    public static final UUID DOCTOR_USER_ID = uuid("00000000-0000-0000-0000-000000000002");
    public static final UUID PATIENT_USER_ID = uuid("00000000-0000-0000-0000-000000000003");
    public static final UUID SECOND_DOCTOR_ID = uuid("00000000-0000-0000-0000-000000000004");
    public static final UUID SECOND_PATIENT_ID = uuid("00000000-0000-0000-0000-000000000005");

    public static final String ADMIN_USERNAME = "admin";
    public static final String DOCTOR_USERNAME = "doctor";
    public static final String PATIENT_USERNAME = "patient";

    public static final String ADMIN_EMAIL = "admin@mediflow.test";
    public static final String DOCTOR_EMAIL = "doctor@mediflow.test";
    public static final String PATIENT_EMAIL = "patient@mediflow.test";

    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String DOCTOR_PASSWORD = "Doctor@123";
    public static final String PATIENT_PASSWORD = "Patient@123";

    public static final UUID APPOINTMENT_1_ID = uuid("00000000-0000-0000-0000-000000000010");
    public static final UUID APPOINTMENT_2_ID = uuid("00000000-0000-0000-0000-000000000011");
    public static final UUID APPOINTMENT_3_ID = uuid("00000000-0000-0000-0000-000000000012");
    public static final UUID APPOINTMENT_4_ID = uuid("00000000-0000-0000-0000-000000000013");

    public static final UUID MEDICAL_RECORD_1_ID = uuid("00000000-0000-0000-0000-000000000020");
    public static final UUID MEDICAL_RECORD_2_ID = uuid("00000000-0000-0000-0000-000000000021");

    public static final UUID INVOICE_1_ID = uuid("00000000-0000-0000-0000-000000000030");
    public static final UUID INVOICE_2_ID = uuid("00000000-0000-0000-0000-000000000031");

    public static final UUID PAYMENT_1_ID = uuid("00000000-0000-0000-0000-000000000040");

    public static final UUID AVAILABILITY_1_ID = uuid("00000000-0000-0000-0000-000000000041");
    public static final UUID AVAILABILITY_2_ID = uuid("00000000-0000-0000-0000-000000000042");

    public static final UUID NOTIFICATION_1_ID = uuid("00000000-0000-0000-0000-000000000050");
    public static final UUID NOTIFICATION_2_ID = uuid("00000000-0000-0000-0000-000000000051");
    public static final UUID NOTIFICATION_3_ID = uuid("00000000-0000-0000-0000-000000000052");

    public static final UUID EVENT_APPT_1_ID = uuid("00000000-0000-0000-0000-000000000060");
    public static final UUID EVENT_APPT_2_ID = uuid("00000000-0000-0000-0000-000000000061");
    public static final UUID EVENT_APPT_3_ID = uuid("00000000-0000-0000-0000-000000000062");
    public static final UUID EVENT_APPT_4_ID = uuid("00000000-0000-0000-0000-000000000063");

    public static final UUID EVENT_INVOICE_1_ID = uuid("00000000-0000-0000-0000-000000000070");
    public static final UUID EVENT_INVOICE_2_ID = uuid("00000000-0000-0000-0000-000000000071");

    public static final UUID EVENT_PAYMENT_1_ID = uuid("00000000-0000-0000-0000-000000000080");

    public static final UUID EVENT_MEDICAL_RECORD_1_ID = uuid("00000000-0000-0000-0000-000000000090");
    public static final UUID EVENT_MEDICAL_RECORD_2_ID = uuid("00000000-0000-0000-0000-000000000091");

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}