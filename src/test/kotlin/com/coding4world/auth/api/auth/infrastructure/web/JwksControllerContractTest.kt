package com.coding4world.auth.api.auth.infrastructure.web

import com.coding4world.auth.api.security.RsaKeyMaterial
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

class JwksControllerContractTest {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(JwksController(RsaKeyMaterial(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)))
                .build()
    }

    @Test
    fun `jwks exposes only public signing key material`() {
        mockMvc.perform(get("/api/v1/auth/jwks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.keys[0].kid").value("auth-api-rs256"))
            .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
            .andExpect(jsonPath("$.keys[0].use").value("sig"))
            .andExpect(jsonPath("$.keys[0].n").isNotEmpty)
            .andExpect(jsonPath("$.keys[0].e").isNotEmpty)
            .andExpect(jsonPath("$.keys[0].d").doesNotExist())
            .andExpect(jsonPath("$.keys[0].p").doesNotExist())
            .andExpect(jsonPath("$.keys[0].q").doesNotExist())
    }
}
