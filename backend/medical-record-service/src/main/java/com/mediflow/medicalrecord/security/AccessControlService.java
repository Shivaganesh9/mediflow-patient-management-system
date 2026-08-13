package com.mediflow.medicalrecord.security;

import com.mediflow.medicalrecord.entity.MedicalRecord;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AccessControlService {

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

    public void assertCanRead(MedicalRecord medicalRecord) {
        if (!canRead(medicalRecord)) {
            throw new AccessDeniedException("You do not have permission to access this medical record");
        }
    }

    public void assertCanWrite(MedicalRecord medicalRecord) {
        if (!canWrite(medicalRecord)) {
            throw new AccessDeniedException("You do not have permission to modify this medical record");
        }
    }

    public boolean canRead(MedicalRecord medicalRecord) {
        if (isAdmin()) {
            return true;
        }
        UUID userId = currentUserId();
        if (isPatient()) {
            return userId != null && medicalRecord.getPatientId().equals(userId);
        }
        if (isDoctor()) {
            return isDoctorRecord(medicalRecord, userId);
        }
        return false;
    }

    public boolean canWrite(MedicalRecord medicalRecord) {
        if (isAdmin()) {
            return true;
        }
        if (isDoctor()) {
            return isDoctorRecord(medicalRecord, currentUserId());
        }
        return false;
    }

    public UUID scopedPatientId(UUID requested) {
        if (isAdmin()) {
            return requested;
        }
        if (isPatient()) {
            UUID userId = requireUserId();
            if (requested != null && !requested.equals(userId)) {
                throw new AccessDeniedException("Patients can only access their own medical records");
            }
            return userId;
        }
        if (isDoctor()) {
            return requested;
        }
        throw new AccessDeniedException("You do not have permission to search medical records");
    }

    public UUID scopedDoctorId(UUID requested) {
        if (isAdmin()) {
            return requested;
        }
        if (isDoctor()) {
            UUID userId = requireUserId();
            if (requested != null && !requested.equals(userId)) {
                throw new AccessDeniedException("Doctors can only access their own medical records");
            }
            return userId;
        }
        if (isPatient()) {
            return requested;
        }
        throw new AccessDeniedException("You do not have permission to search medical records");
    }

    private boolean isDoctorRecord(MedicalRecord medicalRecord, UUID userId) {
        if (userId == null) {
            return false;
        }
        return medicalRecord.getDoctorId() != null && medicalRecord.getDoctorId().equals(userId);
    }

    private UUID requireUserId() {
        UUID userId = currentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Unable to resolve your identity for this operation");
        }
        return userId;
    }
}
