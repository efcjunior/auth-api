package com.coding4world.auth.api.shared.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration

@SpringBootTest
@ActiveProfiles("test")
class AuthApiPropertiesTest(
    @Autowired private val properties: AuthApiProperties,
) {
    @Test
    fun `binds security properties`() {
        assertThat(properties.security.jwt.issuer).isEqualTo("https://auth.test.coding4world.com")
        assertThat(properties.security.jwt.allowedAudiences).containsExactlyInAnyOrder("bdi-api", "another-api")
        assertThat(properties.security.jwt.accessTokenTtl).isEqualTo(Duration.ofMinutes(15))
        assertThat(properties.security.refreshTokenTtl).isEqualTo(Duration.ofDays(7))
    }

    @Test
    fun `binds rate limit defaults`() {
        assertThat(properties.rateLimit.enabled).isTrue()
        assertThat(properties.rateLimit.trustForwardedHeaders).isFalse()
        assertThat(properties.rateLimit.login.capacity).isEqualTo(5)
        assertThat(properties.rateLimit.login.refillPeriod).isEqualTo(Duration.ofMinutes(1))
        assertThat(properties.rateLimit.refresh.capacity).isEqualTo(10)
        assertThat(properties.rateLimit.refresh.refillPeriod).isEqualTo(Duration.ofMinutes(1))
        assertThat(properties.rateLimit.administration.capacity).isEqualTo(5)
        assertThat(properties.rateLimit.administration.refillPeriod).isEqualTo(Duration.ofHours(1))
    }
}
