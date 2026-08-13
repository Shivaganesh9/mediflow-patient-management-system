package com.mediflow.medicalrecord.security;

import com.mediflow.medicalrecord.entity.MedicalRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessControlServiceTest {

    private final AccessControlService accessControl = new AccessControlService();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanReadAndWriteAnyRecord() {
        authenticate("admin", UUID.randomUUID(), List.of("ADMIN"));
        MedicalRecord record = recordFor(UUID.randomUUID(), UUID.randomUUID(), "someone");

        assertTrue(accessControl.canRead(record));
        assertTrue(accessControl.canWrite(record));
        assertTrue(accessControl.isAdmin());
    }

    @Test
    void patientCanReadOwnRecordsOnly() {
        UUID patientId = UUID.randomUUID();
        authenticate("patient1", patientId, List.of("PATIENT"));

        MedicalRecord own = recordFor(patientId, UUID.randomUUID(), "doctor");
        MedicalRecord other = recordFor(UUID.randomUUID(), UUID.randomUUID(), "doctor");

        assertTrue(accessControl.canRead(own));
        assertFalse(accessControl.canRead(other));
        assertThrows(AccessDeniedException.class, () -> accessControl.assertCanRead(other));
    }

    @Test
    void doctorCanReadAssignedRecordsOnly() {
        UUID doctorId = UUID.randomUUID();
        authenticate("doctor1", doctorId, List.of("DOCTOR"));

        MedicalRecord assigned = recordFor(UUID.randomUUID(), doctorId, "someone");
        MedicalRecord authoredOnly = recordFor(UUID.randomUUID(), UUID.randomUUID(), "doctor1");
        MedicalRecord unrelated = recordFor(UUID.randomUUID(), UUID.randomUUID(), "someone");

        assertTrue(accessControl.canRead(assigned));
        assertFalse(accessControl.canRead(authoredOnly));
        assertFalse(accessControl.canRead(unrelated));
        assertThrows(AccessDeniedException.class, () -> accessControl.assertCanWrite(unrelated));
    }

    @Test
    void nurseCannotReadAnyRecord() {
        authenticate("nurse1", UUID.randomUUID(), List.of("NURSE"));
        MedicalRecord record = recordFor(UUID.randomUUID(), UUID.randomUUID(), "doctor");

        assertFalse(accessControl.canRead(record));
        assertThrows(AccessDeniedException.class, () -> accessControl.assertCanRead(record));
    }

    @Test
    void scopedPatientIdForcesPatientScope() {
        UUID patientId = UUID.randomUUID();
        authenticate("patient1", patientId, List.of("PATIENT"));

        assertEquals(patientId, accessControl.scopedPatientId(null));
        assertEquals(patientId, accessControl.scopedPatientId(patientId));
        assertThrows(AccessDeniedException.class,
                () -> accessControl.scopedPatientId(UUID.randomUUID()));
    }

    @Test
    void scopedDoctorIdForcesDoctorScope() {
        UUID doctorId = UUID.randomUUID();
        authenticate("doctor1", doctorId, List.of("DOCTOR"));

        assertEquals(doctorId, accessControl.scopedDoctorId(null));
        assertEquals(doctorId, accessControl.scopedDoctorId(doctorId));
        assertThrows(AccessDeniedException.class,
                () -> accessControl.scopedDoctorId(UUID.randomUUID()));
    }

    @Test
    void adminScopesPassThroughRequestedFilters() {
        UUID requested = UUID.randomUUID();
        authenticate("admin", UUID.randomUUID(), List.of("ADMIN"));

        assertEquals(requested, accessControl.scopedPatientId(requested));
        assertEquals(requested, accessControl.scopedDoctorId(requested));
    }

    @Test
    void currentUserIdIsEmptyWithoutAuthentication() {
        assertNull(accessControl.currentUserId());
        assertNull(accessControl.currentUsername());
        assertFalse(accessControl.currentUser().isPresent());
    }

    private void authenticate(String username, UUID userId, List<String> roles) {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        AuthenticatedUser principal = new AuthenticatedUser(username, userId, roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private MedicalRecord recordFor(UUID patientId, UUID doctorId, String createdBy) {
        MedicalRecord record = new MedicalRecord();
        record.setPatientId(patientId);
        record.setDoctorId(doctorId);
        record.setCreatedBy(createdBy);
        return record;
    }
}
