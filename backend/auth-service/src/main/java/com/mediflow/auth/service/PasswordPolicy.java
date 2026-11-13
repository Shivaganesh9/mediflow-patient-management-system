package com.mediflow.auth.service;

import com.mediflow.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Enforces the password policy before BCrypt hashing. */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    private final AuthProperties properties;

    public void validate(String password, String username) {
        AuthProperties.PasswordPolicy policy = properties.getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < policy.getMinLength()) {
            throw new IllegalArgumentException(
                    "Password must be at least " + policy.getMinLength() + " characters long");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > policy.getMaxBytes()) {
            throw new IllegalArgumentException(
                    "Password must be at most " + policy.getMaxBytes() + " bytes long");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("Password must contain both letters and digits");
        }
        if (policy.isRejectUsername() && username != null && !username.isBlank()
                && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Password must not contain the username");
        }
    }
}
