package com.coding4world.auth.api.auth.infrastructure.web

import com.coding4world.auth.api.auth.application.AuthenticationOperations
import com.coding4world.auth.api.auth.application.AuthenticationTokens
import com.coding4world.auth.api.auth.application.InvalidAudienceException
import com.coding4world.auth.api.auth.application.InvalidAuthenticationException
import com.coding4world.auth.api.shared.config.AuthApiProperties
import com.coding4world.auth.api.shared.web.ApiExceptionHandler
import com.coding4world.auth.api.shared.web.ApiProblemFactory
import com.coding4world.auth.api.shared.web.TraceIdFilter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Duration
import java.time.Instant

class AuthenticationControllerContractTest {
    private val authentication = StubAuthenticationOperations()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        authentication.reset()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    AuthenticationController(
                        authentication,
                        AuthApiProperties(
                            security =
                                AuthApiProperties.Security(
                                    jwt = AuthApiProperties.Jwt(accessTokenTtl = Duration.ofMinutes(15)),
                                ),
                        ),
                    ),
                ).setControllerAdvice(ApiExceptionHandler(ApiProblemFactory()))
                .addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(TraceIdFilter())
                .build()
    }

    @Test
    fun `login returns bearer access and refresh tokens`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com","password":"correct-password","audience":"bdi-api"}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.expiresAt").value("2026-07-14T12:15:00Z"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.refreshTokenExpiresAt").value("2026-07-21T12:00:00Z"))
    }

    @Test
    fun `refresh returns bearer access and refresh tokens`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"refresh-token","audience":"bdi-api"}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
    }

    @Test
    fun `invalid email and missing audience return validation problem`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"invalid","password":"password","audience":""}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `invalid credentials return generic unauthorized problem`() {
        authentication.rejectLogin = true

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com","password":"wrong-password","audience":"bdi-api"}"""),
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_AUTHENTICATION"))
            .andExpect(jsonPath("$.detail").value("The supplied authentication credentials are invalid"))
    }

    @Test
    fun `invalid audience returns bad request problem`() {
        authentication.rejectAudience = true

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com","password":"correct-password","audience":"unknown-api"}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_AUDIENCE"))
            .andExpect(jsonPath("$.detail").value("The requested audience is missing or not allowed"))
    }

    @Test
    fun `logout returns no content`() {
        mockMvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"refresh-token"}"""),
        ).andExpect(status().isNoContent)
    }
}

private class StubAuthenticationOperations : AuthenticationOperations {
    var rejectLogin = false
    var rejectAudience = false

    fun reset() {
        rejectLogin = false
        rejectAudience = false
    }

    override fun login(
        email: String,
        password: String,
        audience: String,
    ): AuthenticationTokens {
        if (rejectAudience) throw InvalidAudienceException()
        if (rejectLogin) throw InvalidAuthenticationException()
        return tokens()
    }

    override fun refresh(
        rawRefreshToken: String,
        audience: String,
    ): AuthenticationTokens {
        if (rejectAudience) throw InvalidAudienceException()
        return tokens()
    }

    override fun logout(rawRefreshToken: String) = Unit

    private fun tokens() =
        AuthenticationTokens(
            accessToken = "access-token",
            accessTokenExpiresAt = Instant.parse("2026-07-14T12:15:00Z"),
            refreshToken = "refresh-token",
            refreshTokenExpiresAt = Instant.parse("2026-07-21T12:00:00Z"),
        )
}
