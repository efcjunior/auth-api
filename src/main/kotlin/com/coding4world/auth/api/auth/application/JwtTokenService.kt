package com.coding4world.auth.api.auth.application

import com.coding4world.auth.api.security.JWT_SIGNING_KEY_ID
import com.coding4world.auth.api.shared.config.AuthApiProperties
import com.coding4world.auth.api.user.domain.model.User
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class JwtTokenService(
    private val jwtEncoder: JwtEncoder,
    private val properties: AuthApiProperties,
    private val clock: Clock,
) {
    fun issue(
        user: User,
        audience: String,
    ): IssuedAccessToken {
        val now = clock.instant()
        val expiresAt = now.plus(properties.security.jwt.accessTokenTtl)
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(properties.security.jwt.issuer)
                .audience(listOf(audience))
                .subject(requireNotNull(user.id))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", user.roles.map { it.name }.sorted())
                .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(JWT_SIGNING_KEY_ID).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return IssuedAccessToken(token, expiresAt)
    }
}

data class IssuedAccessToken(
    val value: String,
    val expiresAt: Instant,
)
