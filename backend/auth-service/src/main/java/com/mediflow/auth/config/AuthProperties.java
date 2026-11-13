package com.mediflow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Authentication lifecycle tuning. All values can be overridden by environment variables
 * documented in docs/runbook.md.
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private PasswordPolicy password = new PasswordPolicy();
    private Refresh refresh = new Refresh();
    private PasswordReset passwordReset = new PasswordReset();

    @Data
    public static class PasswordPolicy {
        private int minLength = 8;
        /** BCrypt only uses the first 72 bytes; reject longer inputs instead of silently truncating. */
        private int maxBytes = 72;
        /** Reject passwords that contain the username (cheap defence against trivial reuse). */
        private boolean rejectUsername = true;
    }

    @Data
    public static class Refresh {
        /**
         * A refresh token presented again shortly after rotation is treated as a benign
         * retry (browser retry, double-submitted request) instead of token theft. Outside
         * this window reuse revokes the whole family.
         */
        private Duration reuseGrace = Duration.ofSeconds(10);
    }

    @Data
    public static class PasswordReset {
        private Duration tokenTtl = Duration.ofMinutes(30);
        /**
         * When true the reset token is returned in the API response. Only for local
         * development and automated tests - there is no mail infrastructure in this project.
         */
        private boolean exposeToken = false;
    }
}
