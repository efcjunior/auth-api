package com.coding4world.auth.api.auth.infrastructure.web

import com.coding4world.auth.api.auth.application.AuthenticationOperations
import com.coding4world.auth.api.auth.application.AuthenticationTokens
import com.coding4world.auth.api.shared.config.AuthApiProperties
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/auth")
class AuthenticationController(
    private val authentication: AuthenticationOperations,
    private val properties: AuthApiProperties,
) {
    @PostMapping("/login")
    @SecurityRequirements
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): AuthenticationResponse =
        authentication
            .login(request.email, request.password, request.audience)
            .toResponse(properties.security.jwt.accessTokenTtl.seconds)

    @PostMapping("/refresh")
    @SecurityRequirements
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): AuthenticationResponse =
        authentication
            .refresh(request.refreshToken, request.audience)
            .toResponse(properties.security.jwt.accessTokenTtl.seconds)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ) {
        authentication.logout(request.refreshToken)
    }
}

data class LoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
    @field:NotBlank val audience: String,
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
    @field:NotBlank val audience: String,
)

data class LogoutRequest(
    @field:NotBlank val refreshToken: String,
)

data class AuthenticationResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val expiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)

private fun AuthenticationTokens.toResponse(expiresIn: Long) =
    AuthenticationResponse(
        accessToken = accessToken,
        tokenType = "Bearer",
        expiresIn = expiresIn,
        expiresAt = accessTokenExpiresAt,
        refreshToken = refreshToken,
        refreshTokenExpiresAt = refreshTokenExpiresAt,
    )
