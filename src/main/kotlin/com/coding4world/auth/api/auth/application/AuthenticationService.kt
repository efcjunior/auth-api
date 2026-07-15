package com.coding4world.auth.api.auth.application

import com.coding4world.auth.api.auth.domain.model.RefreshToken
import com.coding4world.auth.api.auth.domain.port.RefreshTokenRepository
import com.coding4world.auth.api.shared.config.AuthApiProperties
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.port.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.Locale
import java.util.UUID

@Service
class AuthenticationService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
    private val properties: AuthApiProperties,
    private val clock: Clock,
) : AuthenticationOperations {
    private val secureRandom = SecureRandom()
    private val dummyPasswordHash = requireNotNull(passwordEncoder.encode(UUID.randomUUID().toString()))

    override fun login(
        email: String,
        password: String,
        audience: String,
    ): AuthenticationTokens {
        val validatedAudience = validateAudience(audience)
        val user = userRepository.findByNormalizedEmail(normalizeEmail(email))
        val passwordMatches = passwordEncoder.matches(password, user?.passwordHash ?: dummyPasswordHash)
        if (user == null || !user.enabled || !passwordMatches) {
            throw InvalidAuthenticationException()
        }
        return issueTokenPair(user, UUID.randomUUID().toString(), validatedAudience)
    }

    override fun refresh(
        rawRefreshToken: String,
        audience: String,
    ): AuthenticationTokens {
        val validatedAudience = validateAudience(audience)
        val currentHash = hash(rawRefreshToken)
        val currentToken =
            refreshTokenRepository.findByTokenHash(currentHash)
                ?: throw InvalidAuthenticationException()
        val now = clock.instant()
        if (currentToken.revokedAt != null) {
            refreshTokenRepository.revokeFamily(currentToken.familyId, now)
            throw InvalidAuthenticationException()
        }
        if (!currentToken.expiresAt.isAfter(now)) {
            refreshTokenRepository.revokeIfActive(currentHash, now)
            throw InvalidAuthenticationException()
        }

        val user = userRepository.findById(currentToken.userId)
        if (user == null || !user.enabled) {
            refreshTokenRepository.revokeFamily(currentToken.familyId, now)
            throw InvalidAuthenticationException()
        }

        val replacement = createRefreshToken(user, currentToken.familyId)
        val savedReplacement = refreshTokenRepository.save(replacement.persisted)
        val consumed = refreshTokenRepository.revokeIfActive(currentHash, now, replacement.persisted.tokenHash)
        if (!consumed) {
            refreshTokenRepository.revokeFamily(currentToken.familyId, now)
            throw InvalidAuthenticationException()
        }

        return tokenPair(user, replacement.rawValue, savedReplacement, validatedAudience)
    }

    override fun logout(rawRefreshToken: String) {
        refreshTokenRepository.revokeIfActive(hash(rawRefreshToken), clock.instant())
    }

    private fun issueTokenPair(
        user: User,
        familyId: String,
        audience: String,
    ): AuthenticationTokens {
        val refreshToken = createRefreshToken(user, familyId)
        val saved = refreshTokenRepository.save(refreshToken.persisted)
        return tokenPair(user, refreshToken.rawValue, saved, audience)
    }

    private fun tokenPair(
        user: User,
        rawRefreshToken: String,
        persistedRefreshToken: RefreshToken,
        audience: String,
    ): AuthenticationTokens {
        val accessToken = jwtTokenService.issue(user, audience)
        return AuthenticationTokens(
            accessToken = accessToken.value,
            accessTokenExpiresAt = accessToken.expiresAt,
            refreshToken = rawRefreshToken,
            refreshTokenExpiresAt = persistedRefreshToken.expiresAt,
        )
    }

    private fun createRefreshToken(
        user: User,
        familyId: String,
    ): NewRefreshToken {
        val bytes = ByteArray(32).also(secureRandom::nextBytes)
        val rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return NewRefreshToken(
            rawValue = rawValue,
            persisted =
                RefreshToken(
                    tokenHash = hash(rawValue),
                    familyId = familyId,
                    userId = requireNotNull(user.id),
                    expiresAt = clock.instant().plus(properties.security.refreshTokenTtl),
                ),
        )
    }

    private fun validateAudience(audience: String): String {
        val normalizedAudience = audience.trim()
        if (normalizedAudience.isBlank() || normalizedAudience !in properties.security.jwt.allowedAudiences) {
            throw InvalidAudienceException()
        }
        return normalizedAudience
    }

    private fun hash(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)
}

data class AuthenticationTokens(
    val accessToken: String,
    val accessTokenExpiresAt: java.time.Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: java.time.Instant,
)

private data class NewRefreshToken(
    val rawValue: String,
    val persisted: RefreshToken,
)
