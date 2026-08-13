package com.mediflow.doctor.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String UID_CLAIM = "uid";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtProvider.parse(header.substring(7));
                String username = claims.getSubject();
                List<?> roles = claims.get("roles", List.class);
                Collection<SimpleGrantedAuthority> authorities = roles == null ? List.<SimpleGrantedAuthority>of() : roles.stream()
                        .map(String::valueOf).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();

                AuthenticatedUser principal =
                        new AuthenticatedUser(username, extractUserId(claims), extractRoles(roles));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private UUID extractUserId(Claims claims) {
        Object uid = claims.get(UID_CLAIM);
        if (uid != null) {
            try {
                return UUID.fromString(String.valueOf(uid));
            } catch (IllegalArgumentException ignored) {
                // fall through to subject-based resolution
            }
        }
        String subject = claims.getSubject();
        if (subject != null) {
            try {
                return UUID.fromString(subject);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> extractRoles(List<?> roles) {
        return roles == null ? List.of() : roles.stream().map(String::valueOf).toList();
    }
}
