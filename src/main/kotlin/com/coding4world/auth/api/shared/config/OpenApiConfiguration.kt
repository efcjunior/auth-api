package com.coding4world.auth.api.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
    @Bean
    fun authOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Auth API")
                    .description("Authentication and user-management API for Coding4World applications")
                    .version("v1"),
            ).components(
                Components().addSecuritySchemes(
                    SECURITY_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            ).addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME))

    private companion object {
        const val SECURITY_SCHEME = "bearerAuth"
    }
}
