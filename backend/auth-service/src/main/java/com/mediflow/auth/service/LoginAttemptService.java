package com.mediflow.auth.service;

import com.mediflow.auth.audit.AuthAuditService;
import com.mediflow.auth.audit.AuthEventTypes;
import com.mediflow.auth.config.LoginThrottleProperties;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.common.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Failed-login bookkeeping and temporary lockout.
 *
 * <p>Runs in its own transaction ({@code REQUIRES_NEW}) so an increment survives the
 * failed login request, which rolls back for other reasons. The user row is locked while
 * updating to keep concurrent guessing attempts from racing past the threshold.</p>
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final LoginThrottleProperties properties;
    private final AuthAuditService auditService;

    @Transactional(readOnly = true)
    public void assertNotLocked(User user) {
        if (user == null || user.getLockedUntil() == null) {
            return;
        }
        Instant now = Instant.now();
        if (user.getLockedUntil().isAfter(now)) {
            long retryAfter = Math.max(1, Duration.between(now, user.getLockedUntil()).toSeconds());
            auditService.record(AuthEventTypes.LOGIN_LOCKED_OUT, "REJECTED",
                    user.getId(), user.getUsername(), "Account temporarily locked");
            throw new TooManyRequestsException(
                    "Too many failed sign-in attempts. Try again later.", retryAfter);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return;
        }
        Instant now = Instant.now();
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        user.setLastFailedLoginAt(now);
        if (attempts >= properties.getMaxFailedAttempts()) {
            Instant lockedUntil = now.plus(properties.getLockoutDuration());
            user.setLockedUntil(lockedUntil);
            // Start a fresh window once the lock expires instead of locking again immediately.
            user.setFailedLoginAttempts(0);
            log.warn("Account {} locked until {} after {} failed login attempts",
                    user.getUsername(), lockedUntil, attempts);
            auditService.record(AuthEventTypes.LOGIN_LOCKED_OUT, "LOCKED",
                    user.getId(), user.getUsername(), "Locked until " + lockedUntil);
        }
        userRepository.save(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return;
        }
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}
