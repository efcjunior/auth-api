package com.coding4world.auth.api.auth.infrastructure.persistence

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = RefreshTokenDocument.COLLECTION)
data class RefreshTokenDocument(
    @Id val id: String? = null,
    val tokenHash: String,
    val familyId: String,
    val userId: String,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val replacementTokenHash: String? = null,
    @CreatedDate var createdAt: Instant? = null,
    @LastModifiedDate var updatedAt: Instant? = null,
) {
    companion object {
        const val COLLECTION = "refresh_tokens"
    }
}
