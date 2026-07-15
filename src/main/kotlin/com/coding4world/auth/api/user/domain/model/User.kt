package com.coding4world.auth.api.user.domain.model

import java.time.Instant

data class User(
    val id: String? = null,
    val normalizedEmail: String,
    val passwordHash: String,
    val roles: Set<UserRole>,
    val enabled: Boolean,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)

enum class UserRole {
    USER,
    ADMIN,
}
