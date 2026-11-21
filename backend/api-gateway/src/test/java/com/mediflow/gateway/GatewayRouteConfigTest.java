package com.mediflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false"
})
class GatewayRouteConfigTest {

    @Autowired
    private GatewayProperties gatewayProperties;

    @Autowired
    private GlobalCorsProperties globalCorsProperties;

    @Test
    void yamlRoutesAreAuthoritativeAndComplete() {
        Map<String, RouteDefinition> byId = gatewayProperties.getRoutes().stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));

        assertTrue(byId.containsKey("auth-service"));
        assertTrue(byId.containsKey("patient-service"));
        assertTrue(byId.containsKey("appointment-service"));
        assertTrue(byId.containsKey("medical-record-service"));
        assertTrue(byId.containsKey("doctor-service"));
        assertTrue(byId.containsKey("billing-service"));
        assertTrue(byId.containsKey("notification-service"));
        assertTrue(byId.containsKey("analytics-service"));

        assertEquals("http://localhost:8091", byId.get("medical-record-service").getUri().toString());
        assertEquals("http://localhost:8087", byId.get("billing-service").getUri().toString());

        RouteDefinition billing = byId.get("billing-service");
        assertNotNull(billing);
        assertTrue(routeCovers(billing, "/api/v1/invoices/**"),
                "billing-service must route /api/v1/invoices/**");
        assertTrue(routeCovers(billing, "/api/v1/payments/**"),
                "billing-service must route /api/v1/payments/**");
    }

    @Test
    void noDiscoveryUrisRemain() {
        for (RouteDefinition definition : gatewayProperties.getRoutes()) {
            assertFalse(definition.getUri().toString().startsWith("lb://"),
                    "route " + definition.getId() + " must not use the lb:// discovery scheme");
        }
    }

    @Test
    void everyRoutePredicateMatchesItsPathPrefix() {
        List<RouteDefinition> routes = gatewayProperties.getRoutes();
        assertTrue(routes.stream().allMatch(this::hasPathPredicate));
    }

    private boolean hasPathPredicate(RouteDefinition definition) {
        return definition.getPredicates().stream().anyMatch(p -> "Path".equals(p.getName()));
    }

    private boolean routeCovers(RouteDefinition definition, String path) {
        return definition.getPredicates().stream()
                .filter(p -> "Path".equals(p.getName()))
                .anyMatch(p -> p.getArgs().values().stream()
                        .map(String::valueOf)
                        .anyMatch(value -> value.contains(path)));
    }

    @Test
    void gatewayIsSingleCorsOwnerForBrowserTraffic() {
        CorsConfiguration cors = globalCorsProperties.getCorsConfigurations().get("/**");
        assertNotNull(cors, "gateway must define a global CORS configuration for /**");
        assertTrue(cors.getAllowedOrigins().contains("http://localhost:3000"),
                "allowed origin must default to the Vite dev server");
        assertTrue(cors.getAllowCredentials(), "CORS must allow credentials");
        assertTrue(cors.getAllowedMethods().containsAll(
                        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")),
                "CORS must allow the documented HTTP methods");
        assertTrue(cors.getAllowedHeaders().containsAll(List.of("Authorization", "Content-Type")),
                "CORS must allow the Authorization and Content-Type headers");
    }

    @Test
    void corsResponseHeadersFromDownstreamServicesAreDeduped() {
        Optional<FilterDefinition> dedupe = gatewayProperties.getDefaultFilters().stream()
                .filter(f -> "DedupeResponseHeader".equals(f.getName()))
                .findFirst();
        assertTrue(dedupe.isPresent(),
                "gateway must apply a default DedupeResponseHeader filter for CORS headers");
        String args = dedupe.get().getArgs().values().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(" "));
        assertTrue(args.contains("Access-Control-Allow-Origin"));
        assertTrue(args.contains("Access-Control-Allow-Credentials"));
        assertTrue(args.contains("Access-Control-Allow-Methods"));
        assertTrue(args.contains("Access-Control-Allow-Headers"));
        assertTrue(args.contains("RETAIN_FIRST"));
    }
}