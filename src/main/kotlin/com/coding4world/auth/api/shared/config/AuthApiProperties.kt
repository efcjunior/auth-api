package com.coding4world.auth.api.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("auth-api")
data class AuthApiProperties(
    val security: Security = Security(),
    val openApi: OpenApi = OpenApi(),
    val rateLimit: RateLimit = RateLimit(),
) {
    data class Security(
        val jwt: Jwt = Jwt(),
        val refreshTokenTtl: Duration = Duration.ofDays(7),
        val bootstrapAdmin: BootstrapAdmin = BootstrapAdmin(),
    )

    data class Jwt(
        val issuer: String = "https://auth.coding4world.com",
        val allowedAudiences: Set<String> = emptySet(),
        val accessTokenTtl: Duration = Duration.ofMinutes(15),
        val publicKey: String = "",
        val privateKey: String = "",
    )

    data class BootstrapAdmin(
        val email: String = "",
        val password: String = "",
    )

    data class OpenApi(
        val public: Boolean = false,
    )

    data class RateLimit(
        val enabled: Boolean = true,
        val trustForwardedHeaders: Boolean = false,
        val login: Policy = Policy(5, Duration.ofMinutes(1)),
        val refresh: Policy = Policy(10, Duration.ofMinutes(1)),
        val administration: Policy = Policy(5, Duration.ofHours(1)),
    ) {
        data class Policy(
            val capacity: Long,
            val refillPeriod: Duration,
        )
    }
}
