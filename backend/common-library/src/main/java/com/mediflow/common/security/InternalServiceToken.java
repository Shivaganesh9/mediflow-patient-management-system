package com.mediflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Service-to-service credential support for the non-gateway {@code /internal/**} API surface.
 *
 * <p>Internal calls are authenticated with a short-lived JWT that is <em>key-separated</em> from
 * user access tokens: the signing key is derived from the configured {@code JWT_SECRET} with a
 * fixed context string, so a normal user token (or a token minted for a user) can never be
 * accepted as a service credential, and a service credential can never authenticate a user
 * request. No additional secret has to be distributed or hardcoded.</p>
 *
 * <p>A service token carries the conventional {@code iss}/{@code aud}/{@code exp} claims plus
 * {@code type=service} and a narrow {@code scope}. Every internal endpoint must require the
 * specific scope it needs.</p>
 */
public final class InternalServiceToken {

    /** Claim value distinguishing service tokens from access/refresh tokens. */
    public static final String TYPE_SERVICE = "service";
    /** Claim name carrying the granted scopes (space separated, like OAuth 2). */
    public static final String CLAIM_SCOPE = "scope";
    /** Authority prefix used by Spring Security for scopes. */
    public static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

    public static final String SCOPE_DOCTOR_SCHEDULE_VALIDATE = "doctor-schedule:validate";
    public static final String AUTHORITY_DOCTOR_SCHEDULE_VALIDATE =
            SCOPE_AUTHORITY_PREFIX + SCOPE_DOCTOR_SCHEDULE_VALIDATE;

    public static final String ISSUER_APPOINTMENT_SERVICE = "appointment-service";
    public static final String AUDIENCE_DOCTOR_SERVICE = "doctor-service";

    /** Domain separation context: changing it invalidates every previously issued service token. */
    private static final String KEY_CONTEXT = "mediflow.internal-service-token.v1";

    private InternalServiceToken() {
    }

    /**
     * Derives the service-token signing key from the shared JWT secret.
     *
     * <p>HMAC-SHA-256 over the fixed context produces a 256-bit key, which satisfies the HS256
     * minimum even when the configured secret is shorter than 32 bytes.</p>
     */
    public static SecretKey signingKey(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("jwt.secret must be configured for internal service tokens");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Keys.hmacShaKeyFor(mac.doFinal(KEY_CONTEXT.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available for internal service tokens", ex);
        }
    }

    /**
     * Verifies the signature and standard time claims of a service token. Callers remain
     * responsible for checking {@code type}, {@code iss}, {@code aud} and {@code scope}.
     */
    public static Claims parse(String jwtSecret, String token) {
        return Jwts.parser()
                .verifyWith(signingKey(jwtSecret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
