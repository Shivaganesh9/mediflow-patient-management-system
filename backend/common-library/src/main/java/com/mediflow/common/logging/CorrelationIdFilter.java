package com.mediflow.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Establishes a correlation id for every inbound HTTP request.
 *
 * <p>A valid caller-supplied {@code X-Correlation-ID} is reused so a gateway-generated id
 * follows the request into the service; otherwise a new UUID is generated. The id is
 * written to the MDC for logging and echoed in the response header for client-side
 * support.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationContext.sanitize(request.getHeader(CorrelationContext.HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        String previous = CorrelationContext.current();
        CorrelationContext.set(correlationId);
        response.setHeader(CorrelationContext.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previous == null) {
                CorrelationContext.clear();
            } else {
                CorrelationContext.set(previous);
            }
        }
    }
}
