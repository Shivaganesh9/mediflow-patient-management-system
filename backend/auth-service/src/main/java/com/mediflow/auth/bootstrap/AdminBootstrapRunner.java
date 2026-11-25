package com.mediflow.auth.bootstrap;

import com.mediflow.auth.audit.AuthAuditService;
import com.mediflow.auth.audit.AuthEventTypes;
import com.mediflow.auth.config.AuthProperties;
import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.auth.service.PasswordPolicy;
import com.mediflow.common.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates the first production administrator exactly once.
 *
 * <p>Disabled by default. When enabled it runs only while no account holds the ADMIN role, so it
 * can never overwrite, promote or reset an existing administrator. Configuration is validated
 * against the same {@link PasswordPolicy} used by registration and password changes, the account
 * is created ACTIVE, and an {@link AuthEventTypes#ADMIN_BOOTSTRAPPED} audit event records the
 * provisioning without storing the password. Operators are expected to sign in and then disable
 * the feature again.</p>
 *
 * <p>Excluded from the {@code seed} profile: development seed accounts must never be mixed with a
 * production bootstrap administrator.</p>
 */
@Component
@Profile("!seed")
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String ADMIN_ROLE = "ADMIN";

    private final AuthProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthAuditService auditService;

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.Bootstrap config = properties.getBootstrap();
        if (!config.isEnabled()) {
            return;
        }

        // The existence check is the whole safety contract: an administrator that already exists
        // is never modified, promoted or replaced.
        if (userRepository.existsByRoleName(ADMIN_ROLE)) {
            log.info("Administrator bootstrap is enabled but an ADMIN account already exists; skipping");
            return;
        }

        String username = trimmed(config.getUsername());
        String email = trimmed(config.getEmail());
        String password = config.getPassword();
        if (username == null || email == null || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD "
                            + "are required when administrator bootstrap is enabled");
        }
        if (!email.contains("@")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL must be a valid email address");
        }
        passwordPolicy.validate(password, username);
        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException(
                    "Cannot bootstrap administrator: username '" + username + "' is already in use");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "Cannot bootstrap administrator: email '" + email + "' is already in use");
        }

        Role adminRole = roleRepository.findByName(ADMIN_ROLE)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(ADMIN_ROLE)
                        .description("System administrator")
                        .build()));

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName(trimmed(config.getFullName()) == null
                        ? "Initial Administrator" : trimmed(config.getFullName()))
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(adminRole))
                .build();
        user.setCreatedBy("bootstrap");

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent instance may have won the race. That is safe as long as an
            // administrator now exists; anything else is a genuine configuration problem.
            if (userRepository.existsByRoleName(ADMIN_ROLE)) {
                log.warn("Another instance created the bootstrap administrator first; skipping");
                return;
            }
            throw new IllegalStateException("Unable to create the bootstrap administrator", ex);
        }

        auditService.record(AuthEventTypes.ADMIN_BOOTSTRAPPED, "SUCCESS", user.getId(), username,
                "Initial administrator created from bootstrap configuration");
        log.warn("Bootstrap administrator '{}' created. Sign in and disable BOOTSTRAP_ADMIN_ENABLED.",
                username);
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
