package com.mediflow.auth.security;

import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.common.enums.AccountStatus;
import com.mediflow.common.security.RevocationUnavailableException;
import com.mediflow.common.security.SecurityResponses;
import com.mediflow.common.security.TokenRevocationGuard;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final TokenRevocationGuard tokenRevocationGuard;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtProvider.parseClaims(authorizationHeader.substring("Bearer ".length()));

                // A refresh token must never authenticate an API call.
                if (tokenRevocationGuard.isRefreshToken(claims)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                if (tokenRevocationGuard.isRevoked(claims)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                User user = userRepository.findByUsername(claims.getSubject()).orElse(null);
                // Previously issued tokens stop working as soon as the account leaves ACTIVE.
                if (user == null || !AccountStatus.ACTIVE.equals(user.getStatus())) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                        .map(Role::getName)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                UserDetails principal = org.springframework.security.core.userdetails.User
                        .withUsername(user.getUsername())
                        .password("")
                        .authorities(authorities)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RevocationUnavailableException ex) {
                SecurityResponses.serviceUnavailable(response,
                        "Authentication is temporarily unavailable. Please retry shortly.");
                return;
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
