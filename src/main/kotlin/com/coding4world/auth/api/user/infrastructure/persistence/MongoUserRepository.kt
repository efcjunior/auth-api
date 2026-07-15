package com.coding4world.auth.api.user.infrastructure.persistence

import com.coding4world.auth.api.shared.persistence.toMongoPrecision
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.model.UserRole
import com.coding4world.auth.api.user.domain.port.UserRepository
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

internal interface SpringDataUserRepository : MongoRepository<UserDocument, String> {
    fun findByNormalizedEmail(normalizedEmail: String): UserDocument?

    fun existsByRolesContaining(role: UserRole): Boolean
}

@Repository
internal class MongoUserRepository(
    private val repository: SpringDataUserRepository,
) : UserRepository {
    override fun save(user: User): User = repository.save(user.toDocument()).toDomain()

    override fun findByNormalizedEmail(normalizedEmail: String): User? =
        repository.findByNormalizedEmail(normalizedEmail)?.toDomain()

    override fun findById(id: String): User? = repository.findByIdOrNull(id)?.toDomain()

    override fun existsByRole(role: UserRole): Boolean = repository.existsByRolesContaining(role)
}

private fun User.toDocument() =
    UserDocument(
        id = id,
        normalizedEmail = normalizedEmail,
        passwordHash = passwordHash,
        roles = roles,
        enabled = enabled,
        createdAt = createdAt?.toMongoPrecision(),
        updatedAt = updatedAt?.toMongoPrecision(),
    )

private fun UserDocument.toDomain() =
    User(
        id = id,
        normalizedEmail = normalizedEmail,
        passwordHash = passwordHash,
        roles = roles,
        enabled = enabled,
        createdAt = createdAt?.toMongoPrecision(),
        updatedAt = updatedAt?.toMongoPrecision(),
    )
