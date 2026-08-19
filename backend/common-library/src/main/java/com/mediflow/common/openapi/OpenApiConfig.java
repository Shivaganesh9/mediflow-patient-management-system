package com.mediflow.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared OpenAPI/Springdoc configuration for all MediFlow backend services.
 *
 * <p>Registers the {@code bearerAuth} JWT security scheme and a configurable
 * {@link Info} block. Services override {@code springdoc.info.title},
 * {@code springdoc.info.description} and {@code springdoc.info.version} in their
 * own application.yml to tailor the header while keeping the scheme consistent.</p>
 */
@Configuration
public class OpenApiConfig {

    @Value("${springdoc.info.title:MediFlow Service API}")
    private String title;

    @Value("${springdoc.info.description:MediFlow API}")
    private String description;

    @Value("${springdoc.info.version:1.0}")
    private String version;

    @Bean
    public OpenAPI mediflowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
