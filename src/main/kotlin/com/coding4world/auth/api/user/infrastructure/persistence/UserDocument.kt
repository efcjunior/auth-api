package com.coding4world.auth.api.user.infrastructure.persistence

import com.coding4world.auth.api.user.domain.model.UserRole
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = UserDocument.COLLECTION)
data class UserDocument(
    @Id val id: String? = null,
    val normalizedEmail: String,
    val passwordHash: String,
    val roles: Set<UserRole>,
    val enabled: Boolean,
    @CreatedDate var createdAt: Instant? = null,
    @LastModifiedDate var updatedAt: Instant? = null,
) {
    companion object {
        const val COLLECTION = "users"
    }
}
