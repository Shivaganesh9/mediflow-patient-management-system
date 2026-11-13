package com.mediflow.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the shared revocation store. Enabled by default; every service that validates
 * JWTs gets the Redis-backed implementation, so a logout in auth-service immediately
 * invalidates the token for all downstream services.
 *
 * <p>Setting {@code security.revocation.enabled=false} installs the {@link NoOpTokenRevocationRegistry}.
 * That is only appropriate for tests or a local profile - it disables revocation.</p>
 */
@Configuration
public class TokenRevocationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "security.revocation.enabled", havingValue = "true", matchIfMissing = true)
    public TokenRevocationRegistry redisTokenRevocationRegistry(
            StringRedisTemplate redis, TokenRevocationProperties properties) {
        return new RedisTokenRevocationRegistry(redis, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "security.revocation.enabled", havingValue = "false")
    public TokenRevocationRegistry noOpTokenRevocationRegistry() {
        return new NoOpTokenRevocationRegistry();
    }
}
