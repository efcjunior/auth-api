package com.coding4world.auth.api.security

import com.coding4world.auth.api.shared.config.AuthApiProperties
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock

@Configuration
class JwtConfiguration {
    @Bean
    fun jwtEncoder(keys: RsaKeyMaterial): JwtEncoder {
        val rsaKey =
            RSAKey
                .Builder(keys.publicKey)
                .privateKey(keys.privateKey)
                .keyID(JWT_SIGNING_KEY_ID)
                .build()
        return NimbusJwtEncoder(ImmutableJWKSet<SecurityContext>(JWKSet(rsaKey)))
    }

    @Bean
    fun jwtDecoder(
        keys: RsaKeyMaterial,
        properties: AuthApiProperties,
        clock: Clock,
    ): JwtDecoder {
        val decoder =
            NimbusJwtDecoder
                .withPublicKey(keys.publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build()
        val timestampValidator = JwtTimestampValidator().apply { setClock(clock) }
        val issuerValidator =
            JwtValidators.createDefaultWithValidators(
                timestampValidator,
                JwtIssuerValidator(properties.security.jwt.issuer),
            )
        val audienceValidator = allowedAudienceValidator(properties.security.jwt.allowedAudiences)
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(issuerValidator, audienceValidator))
        return decoder
    }

    fun jwtDecoder(
        keys: RsaKeyMaterial,
        properties: AuthApiProperties,
    ): JwtDecoder = jwtDecoder(keys, properties, Clock.systemUTC())

    private fun allowedAudienceValidator(allowedAudiences: Set<String>): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            if (jwt.audience.orEmpty().any { it in allowedAudiences }) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_token", "The token audience is not allowed", null),
                )
            }
        }
}
