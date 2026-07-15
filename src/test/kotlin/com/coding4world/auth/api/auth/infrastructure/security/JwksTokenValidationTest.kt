package com.coding4world.auth.api.auth.infrastructure.security

import com.coding4world.auth.api.auth.application.JwtTokenService
import com.coding4world.auth.api.auth.infrastructure.web.JwksController
import com.coding4world.auth.api.security.JwtConfiguration
import com.coding4world.auth.api.security.RsaKeyMaterial
import com.coding4world.auth.api.shared.config.AuthApiProperties
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.model.UserRole
import tools.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class JwksTokenValidationTest {
    private val clock = Clock.fixed(Instant.now().minusSeconds(60), ZoneOffset.UTC)
    private val properties =
        AuthApiProperties(
            security =
                AuthApiProperties.Security(
                    jwt = AuthApiProperties.Jwt(allowedAudiences = setOf("bdi-api")),
                ),
        )
    private lateinit var server: HttpServer
    private lateinit var jwksUri: String

    @BeforeEach
    fun setUp() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val keys = RsaKeyMaterial(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
        val jwksJson = ObjectMapper().writeValueAsString(JwksController(keys).jwks())
        val tokenService = JwtTokenService(JwtConfiguration().jwtEncoder(keys), properties, clock)
        issuedToken =
            tokenService.issue(
                User(
                    id = "user-1",
                    normalizedEmail = "admin@example.com",
                    passwordHash = "encoded",
                    roles = setOf(UserRole.ADMIN),
                    enabled = true,
                ),
                "bdi-api",
            ).value

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/auth/jwks") { exchange ->
            val body = jwksJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        jwksUri = "http://127.0.0.1:${server.address.port}/api/v1/auth/jwks"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `resource server decoder validates token using published jwks`() {
        val decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build()

        val decoded = decoder.decode(issuedToken)

        assertThat(decoded.headers["kid"]).isEqualTo("auth-api-rs256")
        assertThat(decoded.issuer.toString()).isEqualTo("https://auth.coding4world.com")
        assertThat(decoded.subject).isEqualTo("user-1")
        assertThat(decoded.audience).containsExactly("bdi-api")
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN")
    }

    private lateinit var issuedToken: String
}
