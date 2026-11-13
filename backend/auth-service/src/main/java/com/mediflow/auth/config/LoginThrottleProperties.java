package com.mediflow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Brute-force protection. The lockout is always temporary: a known username can be locked
 * for at most {@code lockoutDuration}, then normal sign-in resumes, so an attacker cannot
 * permanently deny access to a victim account.
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.login")
public class LoginThrottleProperties {

    private int maxFailedAttempts = 5;

    private Duration lockoutDuration = Duration.ofMinutes(15);
}
