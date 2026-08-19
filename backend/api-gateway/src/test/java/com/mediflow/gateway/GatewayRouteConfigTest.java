package com.mediflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false"
})
class GatewayRouteConfigTest {

    @Autowired
    private GatewayProperties gatewayProperties;

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
}