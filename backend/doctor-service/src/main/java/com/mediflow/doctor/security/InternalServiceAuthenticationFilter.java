package com.mediflow.doctor.security;

import com.mediflow.common.security.InternalServiceToken;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Authenticates the non-gateway {@code /internal/**} API with a service credential.
 *
 * <p>Only tokens that are signed with the derived service key, carry {@code type=service}, name
 * appointment-service as issuer, target doctor-service as audience and include a scope are
 * accepted. User access tokens are signed with a different key, so they can never produce a
 * service authentication; they are rejected by the endpoint's scope rule (403). Missing, expired,
 * tampered or incorrectly scoped credentials are left unauthenticated and rejected (401/403).</p>
 */
@Component
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String jwtSecret;

    public InternalServiceAuthenticationFilter(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = InternalServiceToken.parse(jwtSecret, authorization.substring(BEARER_PREFIX.length()));
                if (isTrustedServiceCall(claims)) {
                    List<SimpleGrantedAuthority> authorities = scopes(claims).stream()
                            .map(scope -> new SimpleGrantedAuthority(
                                    InternalServiceToken.SCOPE_AUTHORITY_PREFIX + scope))
                            .toList();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                // Invalid, expired or wrongly signed credential. Do not disturb any user
                // authentication set by the standard filter: the endpoint's scope rule then
                // rejects the request (403), while an anonymous request gets 401.
                // Never log the token.
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isTrustedServiceCall(Claims claims) {
        return InternalServiceToken.TYPE_SERVICE.equals(claims.get(JwtClaims.TYPE, String.class))
                && InternalServiceToken.ISSUER_APPOINTMENT_SERVICE.equals(claims.getIssuer())
                && claims.getAudience() != null
                && claims.getAudience().contains(InternalServiceToken.AUDIENCE_DOCTOR_SERVICE)
                && !scopes(claims).isEmpty();
    }

    private List<String> scopes(Claims claims) {
        String scope = claims.get(InternalServiceToken.CLAIM_SCOPE, String.class);
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scope.trim().split("\\s+")).filter(s -> !s.isBlank()).toList();
    }
}
