package com.mediflow.doctor.identity;

import java.util.UUID;

/**
 * Verifies that a {@code userId} submitted on a profile-creation request references a real
 * auth identity that holds the required role. Implemented by {@link AuthServiceIdentityVerifier},
 * which asks auth-service; mocked in web-layer tests.
 */
public interface IdentityVerifier {

    /**
     * @param userId       the identity referenced by the profile request
     * @param requiredRole the role that identity must hold (e.g. {@code DOCTOR})
     * @throws IllegalArgumentException  when the identity does not exist or holds another role
     * @throws org.springframework.security.access.AccessDeniedException when the caller may not verify identities
     * @throws com.mediflow.common.exception.ConflictException when the identity is not ACTIVE
     * @throws com.mediflow.common.exception.ServiceUnavailableException when auth-service is unreachable
     */
    void verifyIdentity(UUID userId, String requiredRole);
}
