package com.mediflow.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration for the shared access-token revocation store.
 *
 * <pre>
 * security:
 *   revocation:
 *     enabled: true            # false disables revocation entirely (tests/local only)
 *     fail-open: false         # true keeps accepting tokens when Redis is down (NOT for production)
 *     key-prefix: "mediflow:revoke:"
 *     marker-ttl: 7d           # how long a user/session revocation marker is retained
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.revocation")
public class TokenRevocationProperties {

    /** Master switch. Defaults to on so production deployments never run without revocation. */
    private boolean enabled = true;

    /**
     * When the store is unreachable: {@code false} rejects the request (fail closed),
     * {@code true} accepts the token. Fail-closed is the safe default because a revoked
     * token must never be accepted during an outage.
     */
    private boolean failOpen = false;

    private String keyPrefix = "mediflow:revoke:";

    /**
     * Retention for user/session markers. Must be greater than the longest access-token
     * lifetime, otherwise a marker could expire while a revoked token is still valid.
     */
    private Duration markerTtl = Duration.ofDays(7);
}
