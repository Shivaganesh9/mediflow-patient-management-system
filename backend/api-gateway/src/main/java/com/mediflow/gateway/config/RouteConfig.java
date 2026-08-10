package com.mediflow.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r.path("/api/v1/auth/**")
                        .uri("lb://auth-service"))
                .route("patient-service", r -> r.path("/api/v1/patients/**")
                        .uri("lb://patient-service"))
                .route("appointment-service", r -> r.path("/api/v1/appointments/**")
                        .uri("lb://appointment-service"))
                .route("doctor-service", r -> r.path("/api/v1/doctors/**")
                        .uri("lb://doctor-service"))
                .route("billing-service", r -> r.path("/api/v1/invoices/**")
                        .uri("lb://billing-service"))
                .route("notification-service", r -> r.path("/api/v1/notifications/**")
                        .uri("lb://notification-service"))
                .route("analytics-service", r -> r.path("/api/v1/analytics/**")
                        .uri("lb://analytics-service"))
                .build();
    }
}
