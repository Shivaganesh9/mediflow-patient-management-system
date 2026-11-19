package com.mediflow.appointment.schedule;

import com.mediflow.common.security.InternalServiceToken;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints the short-lived, narrowly scoped service credential used for the doctor-service schedule
 * validation call. Tokens are valid for one call only (short TTL plus a unique {@code jti}) and
 * are signed with a key derived from {@code jwt.secret}, separate from user access tokens.
 */
@Component
public class InternalServiceTokenProvider {

    /** Long enough to cover the bounded 1s connect + 3s read budget twice, short enough to be useless if leaked. */
    private static final Duration TOKEN_TTL = Duration.ofSeconds(30);

    private final SecretKey signingKey;

    @Autowired
    public InternalServiceTokenProvider(@Value("${jwt.secret}") String jwtSecret) {
        this(InternalServiceToken.signingKey(jwtSecret));
    }

    /** Test seam: lets unit tests mint tokens with a known derived key. */
    InternalServiceTokenProvider(SecretKey signingKey) {
        this.signingKey = signingKey;
    }

    public String doctorScheduleValidationToken() {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE)
                .issuer(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE)
                .audience().add(InternalServiceToken.AUDIENCE_DOCTOR_SERVICE).and()
                .claim(JwtClaims.TYPE, InternalServiceToken.TYPE_SERVICE)
                .claim(InternalServiceToken.CLAIM_SCOPE,
                        InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(TOKEN_TTL)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
