package com.coding4world.auth.api.auth.domain.port

import com.coding4world.auth.api.auth.domain.model.RefreshToken
import java.time.Instant

interface RefreshTokenRepository {
    fun save(token: RefreshToken): RefreshToken

    fun findByTokenHash(tokenHash: String): RefreshToken?

    fun findAllByFamilyId(familyId: String): List<RefreshToken>

    fun revokeIfActive(
        tokenHash: String,
        revokedAt: Instant,
        replacementTokenHash: String? = null,
    ): Boolean

    fun revokeFamily(
        familyId: String,
        revokedAt: Instant,
    ): Long
}
