package com.mediflow.common.security;

/**
 * Claim and claim-value constants shared by the auth-service (which mints tokens)
 * and every service (which validates them). Keeping the names in one place avoids
 * the "stringly typed" drift that makes cross-service revocation unreliable.
 */
public final class JwtClaims {

    /** Auth {@code User.id}. */
    public static final String UID = "uid";
    /** Role name list (without the {@code ROLE_} prefix). */
    public static final String ROLES = "roles";
    /** Distinguishes access tokens from refresh tokens. */
    public static final String TYPE = "type";
    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";
    /** Refresh-token family id; shared by every token produced by one login session. */
    public static final String SESSION_ID = "sid";
    /**
     * Issued-at with millisecond precision. Standard JWT {@code iat} is truncated to
     * seconds, which is too coarse to compare against a revocation epoch captured in
     * milliseconds: a token minted right after a password change could otherwise be
     * rejected (or an old one accepted) because of the second-level rounding.
     */
    public static final String ISSUED_AT_MILLIS = "iat_ms";

    private JwtClaims() {
    }
}
