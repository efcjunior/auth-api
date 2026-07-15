package com.coding4world.auth.api.security

import com.coding4world.auth.api.shared.config.AuthApiProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class RsaKeyMaterial(
    val publicKey: RSAPublicKey,
    val privateKey: RSAPrivateKey,
)

@Configuration
class RsaKeyConfiguration {
    @Bean
    @Profile("!test & !local")
    fun configuredRsaKeyMaterial(properties: AuthApiProperties): RsaKeyMaterial {
        val publicKeyValue = properties.security.jwt.publicKey
        val privateKeyValue = properties.security.jwt.privateKey
        require(publicKeyValue.isNotBlank() && privateKeyValue.isNotBlank()) {
            "JWT_PUBLIC_KEY and JWT_PRIVATE_KEY must be configured outside local and test profiles"
        }

        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey =
            keyFactory.generatePublic(X509EncodedKeySpec(decodePem(publicKeyValue, "PUBLIC KEY"))) as RSAPublicKey
        val privateKey =
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(decodePem(privateKeyValue, "PRIVATE KEY"))) as RSAPrivateKey
        return RsaKeyMaterial(publicKey, privateKey)
    }

    @Bean
    @Profile("test", "local")
    fun ephemeralRsaKeyMaterial(): RsaKeyMaterial {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val keyPair = generator.generateKeyPair()
        return RsaKeyMaterial(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
    }

    private fun decodePem(
        value: String,
        type: String,
    ): ByteArray {
        val normalized = value.replace("\\n", "\n")
        val base64 =
            normalized
                .replace("-----BEGIN $type-----", "")
                .replace("-----END $type-----", "")
                .replace(Regex("\\s"), "")
        return Base64.getDecoder().decode(base64)
    }
}
