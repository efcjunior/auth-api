package com.coding4world.auth.api.auth.domain.model

import java.time.Instant

data class RefreshToken(
    val id: String? = null,
    val tokenHash: String,
    val familyId: String,
    val userId: String,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val replacementTokenHash: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
