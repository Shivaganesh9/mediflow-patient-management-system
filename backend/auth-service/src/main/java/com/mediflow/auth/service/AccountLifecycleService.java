package com.mediflow.auth.service;

import com.mediflow.auth.audit.AuthAuditService;
import com.mediflow.auth.audit.AuthEventTypes;
import com.mediflow.auth.dto.UpdateAccountStatusRequest;
import com.mediflow.auth.dto.UserProfileResponse;
import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.common.enums.AccountStatus;
import com.mediflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Administrative identity lifecycle: activation, suspension, deactivation and role changes.
 *
 * <p>Every security-sensitive change revokes all sessions of the affected user, so an
 * already-issued access token cannot keep working after a suspension or role change -
 * even in downstream services, because revocation is enforced through the shared store.</p>
 */
@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            "PATIENT", "DOCTOR", "NURSE", "ADMIN", "BILLING_STAFF", "SUPPORT");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SessionRevocationService sessionRevocationService;
    private final AuthAuditService auditService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserProfileResponse> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getUsername))
                .map(this::toProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse getUser(UUID userId) {
        return toProfile(load(userId));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse updateStatus(UUID actorId, UUID targetId, UpdateAccountStatusRequest request) {
        AccountStatus target = request.getStatus();
        if (target == AccountStatus.LOCKED || target == AccountStatus.DELETED) {
            // LOCKED is a temporary login-throttling state, not an administrative one.
            throw new IllegalArgumentException("Unsupported administrative status: " + target
                    + ". Use INACTIVE to deactivate an account.");
        }
        if (actorId.equals(targetId) && target != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Administrators cannot suspend or deactivate their own account");
        }

        User user = load(targetId);
        AccountStatus previous = user.getStatus();
        user.setStatus(target);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        if (target != AccountStatus.ACTIVE) {
            sessionRevocationService.revokeUserSessions(user.getId(), "STATUS_" + target);
        }
        auditService.record(AuthEventTypes.ACCOUNT_STATUS_CHANGED, "SUCCESS", user.getId(), user.getUsername(),
                previous + " -> " + target + (request.getReason() == null ? "" : ": " + request.getReason()));
        return toProfile(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse updateRoles(UUID actorId, UUID targetId, Set<String> requestedRoles) {
        Set<Role> roles = new HashSet<>();
        for (String name : requestedRoles) {
            String normalized = name.trim().toUpperCase();
            if (!ASSIGNABLE_ROLES.contains(normalized)) {
                throw new IllegalArgumentException("Unknown role: " + name);
            }
            // The whitelist above bounds what can be created, so a missing catalogue row
            // (billing-staff/nurse/support are not all seeded by V1) is created on demand
            // instead of failing the administration request.
            roles.add(roleRepository.findByName(normalized)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name(normalized)
                            .description("Role " + normalized)
                            .build())));
        }

        if (actorId.equals(targetId) && roles.stream().noneMatch(role -> "ADMIN".equals(role.getName()))) {
            throw new IllegalArgumentException("Administrators cannot remove their own ADMIN role");
        }

        User user = load(targetId);
        Set<String> previous = roleNames(user);
        user.setRoles(roles);
        userRepository.save(user);

        // Roles are embedded in access tokens, so existing tokens must be discarded.
        sessionRevocationService.revokeUserSessions(user.getId(), "ROLES_CHANGED");
        auditService.record(AuthEventTypes.ACCOUNT_ROLES_CHANGED, "SUCCESS", user.getId(), user.getUsername(),
                previous + " -> " + roleNames(user));
        return toProfile(user);
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(roleNames(user))
                .build();
    }

    private Set<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    }
}
