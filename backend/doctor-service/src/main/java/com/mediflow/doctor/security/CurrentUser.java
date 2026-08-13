package com.mediflow.doctor.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CurrentUser {
    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public String currentUsername() {
        return currentUser().map(AuthenticatedUser::username).orElse(null);
    }

    public UUID currentUserId() {
        return currentUser().map(AuthenticatedUser::userId).orElse(null);
    }

    public UUID requireCurrentUserId() {
        UUID userId = currentUserId();
        if (userId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unable to resolve your identity for this operation");
        }
        return userId;
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
}