package com.mediflow.billing.security;

import com.mediflow.billing.entity.Invoice;
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
    void adminCanReadAnyInvoice() {
        authenticate("admin", UUID.randomUUID(), List.of("ADMIN"));
        Invoice invoice = invoiceFor(UUID.randomUUID());

        assertTrue(accessControl.canRead(invoice));
        assertTrue(accessControl.hasStaffAccess());
    }

    @Test
    void billingStaffCanReadAnyInvoice() {
        authenticate("billing1", UUID.randomUUID(), List.of("BILLING_STAFF"));
        Invoice invoice = invoiceFor(UUID.randomUUID());

        assertTrue(accessControl.canRead(invoice));
        assertTrue(accessControl.hasStaffAccess());
    }

    @Test
    void patientCanReadOwnInvoicesOnly() {
        UUID patientId = UUID.randomUUID();
        authenticate("patient1", patientId, List.of("PATIENT"));

        Invoice own = invoiceFor(patientId);
        Invoice other = invoiceFor(UUID.randomUUID());

        assertTrue(accessControl.canRead(own));
        assertFalse(accessControl.canRead(other));
        assertThrows(AccessDeniedException.class, () -> accessControl.assertCanRead(other));
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
    void nonPatientRolesPassThroughRequestedScope() {
        UUID requested = UUID.randomUUID();
        authenticate("doctor1", UUID.randomUUID(), List.of("DOCTOR"));

        assertEquals(requested, accessControl.scopedPatientId(requested));
        assertTrue(accessControl.canRead(invoiceFor(UUID.randomUUID())));
    }

    @Test
    void clinicalStaffCanReadAnyInvoice() {
        for (String role : List.of("DOCTOR", "NURSE", "SUPPORT")) {
            authenticate(role.toLowerCase(), UUID.randomUUID(), List.of(role));

            assertTrue(accessControl.canRead(invoiceFor(UUID.randomUUID())));
            assertTrue(accessControl.hasStaffAccess());
        }
    }

    @Test
    void unknownRoleCannotReadOrScopeInvoices() {
        authenticate("legacy-staff", UUID.randomUUID(), List.of("STAFF"));

        assertFalse(accessControl.canRead(invoiceFor(UUID.randomUUID())));
        assertFalse(accessControl.hasStaffAccess());
        assertThrows(AccessDeniedException.class,
                () -> accessControl.scopedPatientId(UUID.randomUUID()));
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

    private Invoice invoiceFor(UUID patientId) {
        Invoice invoice = new Invoice();
        invoice.setPatientId(patientId);
        return invoice;
    }
}