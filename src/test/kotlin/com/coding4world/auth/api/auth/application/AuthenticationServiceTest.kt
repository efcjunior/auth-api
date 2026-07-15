package com.coding4world.auth.api.auth.application

import com.coding4world.auth.api.auth.domain.model.RefreshToken
import com.coding4world.auth.api.auth.domain.port.RefreshTokenRepository
import com.coding4world.auth.api.security.JwtConfiguration
import com.coding4world.auth.api.security.RsaKeyMaterial
import com.coding4world.auth.api.shared.config.AuthApiProperties
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.model.UserRole
import com.coding4world.auth.api.user.domain.port.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuthenticationServiceTest {
    private val now = Instant.parse("2026-07-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties =
        AuthApiProperties(
            security =
                AuthApiProperties.Security(
                    jwt = AuthApiProperties.Jwt(allowedAudiences = setOf("bdi-api", "another-api")),
                ),
        )
    private val users = InMemoryUserRepository()
    private val refreshTokens = InMemoryRefreshTokenRepository()
    private val passwordEncoder = TestPasswordEncoder()
    private lateinit var jwtConfiguration: JwtConfiguration
    private lateinit var keys: RsaKeyMaterial
    private lateinit var service: AuthenticationService

    @BeforeEach
    fun setUp() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        keys = RsaKeyMaterial(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
        jwtConfiguration = JwtConfiguration()
        val jwtTokenService = JwtTokenService(jwtConfiguration.jwtEncoder(keys), properties, clock)
        service = AuthenticationService(users, refreshTokens, passwordEncoder, jwtTokenService, properties, clock)
        users.save(
            User(
                id = "user-1",
                normalizedEmail = "admin@example.com",
                passwordHash = requireNotNull(passwordEncoder.encode("correct-password")),
                roles = setOf(UserRole.ADMIN),
                enabled = true,
            ),
        )
    }

    @Test
    fun `login issues signed access and opaque refresh tokens for requested audience`() {
        val result = service.login("  ADMIN@EXAMPLE.COM ", "correct-password", "bdi-api")
        val decoded = jwtConfiguration.jwtDecoder(keys, properties, clock).decode(result.accessToken)

        assertThat(decoded.issuer.toString()).isEqualTo("https://auth.coding4world.com")
        assertThat(decoded.subject).isEqualTo("user-1")
        assertThat(decoded.audience).containsExactly("bdi-api")
        assertThat(decoded.id).isNotBlank()
        assertThat(decoded.issuedAt).isEqualTo(now)
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN")
        assertThat(decoded.expiresAt).isEqualTo(now.plusSeconds(900))
        assertThat(result.refreshToken).hasSize(43)
        assertThat(refreshTokens.findByTokenHash(hash(result.refreshToken))).isNotNull()
        assertThat(refreshTokens.findByTokenHash(result.refreshToken)).isNull()
    }

    @Test
    fun `login rejects missing or unknown audience`() {
        assertThrows<InvalidAudienceException> {
            service.login("admin@example.com", "correct-password", " ")
        }

        assertThrows<InvalidAudienceException> {
            service.login("admin@example.com", "correct-password", "unknown-api")
        }

        assertThat(refreshTokens.tokens).isEmpty()
    }

    @Test
    fun `invalid password unknown user and disabled user return generic authentication failure`() {
        assertThrows<InvalidAuthenticationException> {
            service.login("admin@example.com", "wrong-password", "bdi-api")
        }
        assertThrows<InvalidAuthenticationException> {
            service.login("missing@example.com", "correct-password", "bdi-api")
        }

        users.save(users.findById("user-1")!!.copy(enabled = false))
        assertThrows<InvalidAuthenticationException> {
            service.login("admin@example.com", "correct-password", "bdi-api")
        }

        assertThat(refreshTokens.tokens).isEmpty()
    }

    @Test
    fun `refresh rotates a token and consumes the previous token`() {
        val login = service.login("admin@example.com", "correct-password", "bdi-api")

        val rotated = service.refresh(login.refreshToken, "another-api")
        val decoded = jwtConfiguration.jwtDecoder(keys, properties, clock).decode(rotated.accessToken)

        val oldToken = refreshTokens.findByTokenHash(hash(login.refreshToken))
        assertThat(decoded.audience).containsExactly("another-api")
        assertThat(rotated.refreshToken).isNotEqualTo(login.refreshToken)
        assertThat(oldToken?.revokedAt).isEqualTo(now)
        assertThat(oldToken?.replacementTokenHash).isEqualTo(hash(rotated.refreshToken))
        assertThat(refreshTokens.findByTokenHash(hash(rotated.refreshToken))?.revokedAt).isNull()
    }

    @Test
    fun `refresh rejects invalid audience without consuming token`() {
        val login = service.login("admin@example.com", "correct-password", "bdi-api")

        assertThrows<InvalidAudienceException> {
            service.refresh(login.refreshToken, "unknown-api")
        }

        assertThat(refreshTokens.findByTokenHash(hash(login.refreshToken))?.revokedAt).isNull()
    }

    @Test
    fun `reuse of a rotated token revokes its entire token family`() {
        val login = service.login("admin@example.com", "correct-password", "bdi-api")
        service.refresh(login.refreshToken, "bdi-api")

        assertThrows<InvalidAuthenticationException> { service.refresh(login.refreshToken, "bdi-api") }

        val familyId = refreshTokens.findByTokenHash(hash(login.refreshToken))!!.familyId
        assertThat(refreshTokens.findAllByFamilyId(familyId)).allMatch { it.revokedAt == now }
    }

    @Test
    fun `expired refresh token is revoked and rejected`() {
        val expired =
            refreshTokens.save(
                RefreshToken(
                    tokenHash = hash("expired-token"),
                    familyId = "family-expired",
                    userId = "user-1",
                    expiresAt = now.minusSeconds(1),
                ),
            )

        assertThrows<InvalidAuthenticationException> {
            service.refresh("expired-token", "bdi-api")
        }

        assertThat(refreshTokens.findByTokenHash(expired.tokenHash)?.revokedAt).isEqualTo(now)
    }

    @Test
    fun `refresh revokes token family when user is disabled or missing`() {
        val disabledLogin = service.login("admin@example.com", "correct-password", "bdi-api")
        users.save(users.findById("user-1")!!.copy(enabled = false))

        assertThrows<InvalidAuthenticationException> {
            service.refresh(disabledLogin.refreshToken, "bdi-api")
        }

        val disabledFamilyId = refreshTokens.findByTokenHash(hash(disabledLogin.refreshToken))!!.familyId
        assertThat(refreshTokens.findAllByFamilyId(disabledFamilyId)).allMatch { it.revokedAt == now }

        users.remove("user-1")
        val missing =
            refreshTokens.save(
                RefreshToken(
                    tokenHash = hash("missing-user-token"),
                    familyId = "family-missing",
                    userId = "user-1",
                    expiresAt = now.plusSeconds(60),
                ),
            )

        assertThrows<InvalidAuthenticationException> {
            service.refresh("missing-user-token", "bdi-api")
        }

        assertThat(refreshTokens.findAllByFamilyId(missing.familyId)).allMatch { it.revokedAt == now }
    }

    @Test
    fun `logout revokes the supplied refresh token and is idempotent`() {
        val login = service.login("admin@example.com", "correct-password", "bdi-api")

        service.logout(login.refreshToken)
        service.logout(login.refreshToken)

        assertThat(refreshTokens.findByTokenHash(hash(login.refreshToken))?.revokedAt).isEqualTo(now)
    }

    private fun hash(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private class TestPasswordEncoder : PasswordEncoder {
    override fun encode(rawPassword: CharSequence?): String? = rawPassword?.let { "encoded:$it" }

    override fun matches(
        rawPassword: CharSequence?,
        encodedPassword: String?,
    ): Boolean = rawPassword != null && encode(rawPassword) == encodedPassword
}

private class InMemoryUserRepository : UserRepository {
    private val users = linkedMapOf<String, User>()

    override fun save(user: User): User {
        val saved = user.copy(id = user.id ?: "user-${users.size + 1}")
        users[requireNotNull(saved.id)] = saved
        return saved
    }

    override fun findByNormalizedEmail(normalizedEmail: String): User? =
        users.values.firstOrNull { it.normalizedEmail == normalizedEmail }

    override fun findById(id: String): User? = users[id]

    override fun existsByRole(role: UserRole): Boolean = users.values.any { role in it.roles }

    fun remove(id: String) {
        users.remove(id)
    }
}

private class InMemoryRefreshTokenRepository : RefreshTokenRepository {
    val tokens = linkedMapOf<String, RefreshToken>()

    override fun save(token: RefreshToken): RefreshToken {
        val saved = token.copy(id = token.id ?: "token-${tokens.size + 1}")
        tokens[saved.tokenHash] = saved
        return saved
    }

    override fun findByTokenHash(tokenHash: String): RefreshToken? = tokens[tokenHash]

    override fun findAllByFamilyId(familyId: String): List<RefreshToken> =
        tokens.values.filter { it.familyId == familyId }

    override fun revokeIfActive(
        tokenHash: String,
        revokedAt: Instant,
        replacementTokenHash: String?,
    ): Boolean {
        val token = tokens[tokenHash] ?: return false
        if (token.revokedAt != null) return false
        tokens[tokenHash] = token.copy(revokedAt = revokedAt, replacementTokenHash = replacementTokenHash)
        return true
    }

    override fun revokeFamily(
        familyId: String,
        revokedAt: Instant,
    ): Long {
        var revoked = 0L
        tokens.entries.forEach { entry ->
            if (entry.value.familyId == familyId && entry.value.revokedAt == null) {
                entry.setValue(entry.value.copy(revokedAt = revokedAt))
                revoked++
            }
        }
        return revoked
    }
}
