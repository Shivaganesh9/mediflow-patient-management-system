package com.mediflow.billing.security;

import com.mediflow.billing.entity.Invoice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class AccessControlService {

    /**
     * Roles allowed to read every invoice. Billing reads are intentionally
     * organisation-wide for authenticated care/finance staff, but the set is
     * explicit so unknown or future roles are denied instead of implicitly
     * allowed. Patient reads are scoped to the caller's own invoices below.
     */
    private static final Set<String> READ_ALL_ROLES =
            Set.of("BILLING_STAFF", "DOCTOR", "NURSE", "SUPPORT");

    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.username();
        }
        return authentication.getPrincipal() instanceof String username ? username : null;
    }

    public UUID currentUserId() {
        return currentUser().map(AuthenticatedUser::userId).orElse(null);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isPatient() {
        return hasRole("PATIENT");
    }

    public boolean hasStaffAccess() {
        return isAdmin() || READ_ALL_ROLES.stream().anyMatch(this::hasRole);
    }

    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String expected = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(expected::equals);
    }

    public boolean canRead(Invoice invoice) {
        if (hasStaffAccess()) {
            return true;
        }
        UUID userId = currentUserId();
        if (isPatient()) {
            return userId != null && invoice.getPatientId().equals(userId);
        }
        return false;
    }

    public void assertCanRead(Invoice invoice) {
        if (!canRead(invoice)) {
            throw new AccessDeniedException("You do not have permission to access this invoice");
        }
    }

    public UUID scopedPatientId(UUID requested) {
        if (isPatient()) {
            UUID userId = requireUserId();
            if (requested != null && !requested.equals(userId)) {
                throw new AccessDeniedException("Patients can only access their own invoices");
            }
            return userId;
        }
        if (hasStaffAccess()) {
            return requested;
        }
        throw new AccessDeniedException("You do not have permission to access invoices");
    }

    private UUID requireUserId() {
        UUID userId = currentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unable to resolve your identity for this operation");
        }
        return userId;
    }
}