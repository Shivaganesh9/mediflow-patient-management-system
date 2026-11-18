package com.mediflow.appointment.security;

import com.mediflow.appointment.entity.Appointment;
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
    void adminCanAccessAnyAppointment() {
        authenticate("admin", UUID.randomUUID(), List.of("ADMIN"));
        Appointment appointment = appointmentFor(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(accessControl.canAccess(appointment));
        assertTrue(accessControl.isAdmin());
        assertEquals(appointment.getPatientId(), accessControl.scopedPatientId(appointment.getPatientId()));
        assertEquals(appointment.getDoctorId(), accessControl.scopedDoctorId(appointment.getDoctorId()));
    }

    @Test
    void staffRolesCanAccessAnyAppointment() {
        authenticate("nurse1", UUID.randomUUID(), List.of("NURSE"));
        Appointment appointment = appointmentFor(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(accessControl.canAccess(appointment));
        assertTrue(accessControl.hasStaffAccess());
    }

    @Test
    void patientCanAccessOwnAppointmentsOnly() {
        UUID patientId = UUID.randomUUID();
        authenticate("patient1", patientId, List.of("PATIENT"));

        Appointment own = appointmentFor(patientId, UUID.randomUUID());
        Appointment other = appointmentFor(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(accessControl.canAccess(own));
        assertFalse(accessControl.canAccess(other));
        assertThrows(AccessDeniedException.class, () -> accessControl.assertCanAccess(other));
    }

    @Test
    void doctorCanAccessOwnAppointmentsOnly() {
        UUID doctorId = UUID.randomUUID();
        authenticate("doctor1", doctorId, List.of("DOCTOR"));

        Appointment own = appointmentFor(UUID.randomUUID(), doctorId);
        Appointment other = appointmentFor(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(accessControl.canAccess(own));
        assertFalse(accessControl.canAccess(other));
        assertThrows(AccessDeniedException.class, () -> accessControl.assertCanAccess(other));
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
    void patientCreatePassesRequestedDoctorThrough() {
        UUID patientId = UUID.randomUUID();
        UUID requestedDoctor = UUID.randomUUID();
        authenticate("patient1", patientId, List.of("PATIENT"));

        assertEquals(patientId, accessControl.scopedPatientId(patientId));
        assertEquals(requestedDoctor, accessControl.scopedDoctorId(requestedDoctor));
    }

    @Test
    void doctorCreatePassesRequestedPatientThrough() {
        UUID doctorId = UUID.randomUUID();
        UUID requestedPatient = UUID.randomUUID();
        authenticate("doctor1", doctorId, List.of("DOCTOR"));

        assertEquals(doctorId, accessControl.scopedDoctorId(doctorId));
        assertEquals(requestedPatient, accessControl.scopedPatientId(requestedPatient));
    }

    @Test
    void unsupportedRoleCannotAccessAppointments() {
        authenticate("auditor", UUID.randomUUID(), List.of("AUDITOR"));

        assertFalse(accessControl.canAccess(appointmentFor(UUID.randomUUID(), UUID.randomUUID())));
        assertThrows(AccessDeniedException.class,
                () -> accessControl.scopedPatientId(UUID.randomUUID()));
    }

    @Test
    void clinicalRolesMayChangeClinicalState() {
        for (String role : List.of("ADMIN", "DOCTOR", "NURSE")) {
            authenticate(role.toLowerCase(), UUID.randomUUID(), List.of(role));
            assertTrue(accessControl.hasClinicalAccess(), role + " should be clinical");
            accessControl.assertClinicalAccess();
        }
    }

    @Test
    void nonClinicalStaffMayNotChangeClinicalState() {
        for (String role : List.of("BILLING_STAFF", "SUPPORT", "PATIENT")) {
            authenticate(role.toLowerCase(), UUID.randomUUID(), List.of(role));
            assertFalse(accessControl.hasClinicalAccess(), role + " should not be clinical");
            assertThrows(AccessDeniedException.class, accessControl::assertClinicalAccess);
        }
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

    private Appointment appointmentFor(UUID patientId, UUID doctorId) {
        Appointment appointment = new Appointment();
        appointment.setPatientId(patientId);
        appointment.setDoctorId(doctorId);
        return appointment;
    }
}