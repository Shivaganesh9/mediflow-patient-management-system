package com.mediflow.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ensures every proxied request carries an {@code X-Correlation-ID}.
 *
 * <p>Downstream services reuse the header for MDC logging, error responses and domain-event
 * envelopes, so one id links a browser request, the synchronous service calls it makes and the
 * asynchronous events those calls produce. Caller-supplied values are accepted only when short and
 * safe; anything else is replaced with a new UUID.</p>
 */
@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    private static final String HEADER = "X-Correlation-ID";
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = supplied != null && SAFE_VALUE.matcher(supplied.trim()).matches()
                ? supplied.trim()
                : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
