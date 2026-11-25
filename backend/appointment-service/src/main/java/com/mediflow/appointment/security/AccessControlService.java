package com.mediflow.appointment.security;

import com.mediflow.appointment.entity.Appointment;
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

    private static final Set<String> STAFF_ROLES = Set.of("NURSE", "BILLING_STAFF", "SUPPORT");

    /**
     * Roles allowed to create, reschedule, confirm or cancel an appointment. BILLING_STAFF and
     * SUPPORT are intentionally read-only: billing staff own invoices and payments, support staff
     * answer operational questions, and neither may change scheduling state. Clinical transitions
     * (start/complete/no-show) are narrower still and require {@link #hasClinicalAccess()}.
     */
    private static final Set<String> MUTATION_ROLES =
            Set.of("ADMIN", "DOCTOR", "NURSE", "PATIENT");

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

    public boolean isDoctor() {
        return hasRole("DOCTOR");
    }

    public boolean hasStaffAccess() {
        return isAdmin() || hasAnyRole(STAFF_ROLES);
    }

    /**
     * Roles allowed to drive the clinical part of the appointment lifecycle (start, complete,
     * no-show). Billing/support staff can read appointments for their work but must not change
     * clinical state.
     */
    public boolean hasClinicalAccess() {
        return isAdmin() || isDoctor() || hasRole("NURSE");
    }

    public void assertClinicalAccess() {
        if (!hasClinicalAccess()) {
            throw new AccessDeniedException(
                    "Only clinical staff may change the clinical state of an appointment");
        }
    }

    /** True when the caller may create, reschedule, confirm or cancel appointments. */
    public boolean canMutate() {
        return hasAnyRole(MUTATION_ROLES);
    }

    public void assertCanMutate() {
        if (!canMutate()) {
            throw new AccessDeniedException(
                    "Your role can read appointments but cannot change them");
        }
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

    public boolean canAccess(Appointment appointment) {
        if (hasStaffAccess()) {
            return true;
        }
        UUID userId = currentUserId();
        if (isPatient()) {
            return userId != null && appointment.getPatientId().equals(userId);
        }
        if (isDoctor()) {
            return userId != null && appointment.getDoctorId().equals(userId);
        }
        return false;
    }

    public void assertCanAccess(Appointment appointment) {
        if (!canAccess(appointment)) {
            throw new AccessDeniedException("You do not have permission to access this appointment");
        }
    }

    public UUID scopedPatientId(UUID requested) {
        if (hasStaffAccess()) {
            return requested;
        }
        if (isPatient()) {
            UUID userId = requireUserId();
            if (requested != null && !requested.equals(userId)) {
                throw new AccessDeniedException("Patients can only access their own appointments");
            }
            return userId;
        }
        if (isDoctor()) {
            return requested;
        }
        throw new AccessDeniedException("You do not have permission to access appointments");
    }

    public UUID scopedDoctorId(UUID requested) {
        if (hasStaffAccess()) {
            return requested;
        }
        if (isDoctor()) {
            UUID userId = requireUserId();
            if (requested != null && !requested.equals(userId)) {
                throw new AccessDeniedException("Doctors can only access their own appointments");
            }
            return userId;
        }
        if (isPatient()) {
            return requested;
        }
        throw new AccessDeniedException("You do not have permission to access appointments");
    }

    private boolean hasAnyRole(Set<String> roles) {
        return roles.stream().anyMatch(this::hasRole);
    }

    private UUID requireUserId() {
        UUID userId = currentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unable to resolve your identity for this operation");
        }
        return userId;
    }
}