package com.coding4world.auth.api.shared.ratelimit

import com.coding4world.auth.api.shared.config.AuthApiProperties

enum class RateLimitPolicy {
    LOGIN,
    REFRESH,
    ADMINISTRATION,
}

fun AuthApiProperties.RateLimit.policy(policy: RateLimitPolicy): AuthApiProperties.RateLimit.Policy =
    when (policy) {
        RateLimitPolicy.LOGIN -> login
        RateLimitPolicy.REFRESH -> refresh
        RateLimitPolicy.ADMINISTRATION -> administration
    }
