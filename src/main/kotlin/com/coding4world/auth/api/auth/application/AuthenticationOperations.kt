package com.coding4world.auth.api.auth.application

interface AuthenticationOperations {
    fun login(
        email: String,
        password: String,
        audience: String,
    ): AuthenticationTokens

    fun refresh(
        rawRefreshToken: String,
        audience: String,
    ): AuthenticationTokens

    fun logout(rawRefreshToken: String)
}
