package com.coding4world.auth.api.shared.ratelimit

import com.coding4world.auth.api.shared.config.AuthApiProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class RateLimitIdentityResolver {
    fun resolve(
        policy: RateLimitPolicy,
        request: HttpServletRequest,
        properties: AuthApiProperties.RateLimit,
    ): String? =
        when (policy) {
            RateLimitPolicy.LOGIN,
            RateLimitPolicy.REFRESH,
            -> "ip:${request.clientIp(properties)}"

            RateLimitPolicy.ADMINISTRATION ->
                if (isAdministrator()) {
                    authenticatedUser()?.let { "admin:$it" }
                } else {
                    null
                }
        }

    private fun HttpServletRequest.clientIp(properties: AuthApiProperties.RateLimit): String {
        if (properties.trustForwardedHeaders) {
            firstForwardedFor()?.let { return it }
            forwardedHeaderFor()?.let { return it }
        }
        return remoteAddr.orEmpty().ifBlank { "unknown" }
    }

    private fun HttpServletRequest.firstForwardedFor(): String? =
        getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun HttpServletRequest.forwardedHeaderFor(): String? =
        getHeader("Forwarded")
            ?.split(';', ',')
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("for=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf(String::isNotBlank)

    private fun authenticatedUser(): String? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (!authentication.isAuthenticated) return null
        return when (val principal = authentication.principal) {
            is Jwt -> principal.subject
            is String -> principal
            else -> authentication.name
        }?.takeIf(String::isNotBlank)
    }

    private fun isAdministrator(): Boolean =
        SecurityContextHolder
            .getContext()
            .authentication
            ?.authorities
            ?.any { it.authority == "ROLE_ADMIN" }
            ?: false
}
