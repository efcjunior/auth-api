package com.coding4world.auth.api.auth.infrastructure.web

import com.coding4world.auth.api.security.JWT_SIGNING_KEY_ID
import com.coding4world.auth.api.security.RsaKeyMaterial
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class JwksController(
    private val keys: RsaKeyMaterial,
) {
    @GetMapping("/jwks")
    @SecurityRequirements
    fun jwks(): Map<String, Any> {
        val publicKey =
            RSAKey
                .Builder(keys.publicKey)
                .keyID(JWT_SIGNING_KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build()
        return JWKSet(publicKey).toJSONObject(false)
    }
}
