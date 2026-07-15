package com.coding4world.auth.api.shared.ratelimit

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class RateLimitPolicyResolver {
    fun resolve(request: HttpServletRequest): RateLimitPolicy? {
        val path = request.pathWithinApplication()
        return when {
            request.method == "POST" && path == "/api/v1/auth/login" -> RateLimitPolicy.LOGIN
            request.method == "POST" && path == "/api/v1/auth/refresh" -> RateLimitPolicy.REFRESH
            path.startsWith("/api/v1/admin/") -> RateLimitPolicy.ADMINISTRATION
            else -> null
        }
    }

    private fun HttpServletRequest.pathWithinApplication(): String {
        val context = contextPath.orEmpty()
        return if (context.isNotBlank() && requestURI.startsWith(context)) {
            requestURI.removePrefix(context)
        } else {
            requestURI
        }
    }
}
