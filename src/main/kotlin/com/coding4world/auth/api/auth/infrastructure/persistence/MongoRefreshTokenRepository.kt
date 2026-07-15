package com.coding4world.auth.api.auth.infrastructure.persistence

import com.coding4world.auth.api.auth.domain.model.RefreshToken
import com.coding4world.auth.api.auth.domain.port.RefreshTokenRepository
import com.coding4world.auth.api.shared.persistence.toMongoPrecision
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

internal interface SpringDataRefreshTokenRepository : MongoRepository<RefreshTokenDocument, String> {
    fun findByTokenHash(tokenHash: String): RefreshTokenDocument?

    fun findAllByFamilyId(familyId: String): List<RefreshTokenDocument>
}

@Repository
internal class MongoRefreshTokenRepository(
    private val repository: SpringDataRefreshTokenRepository,
    private val mongoTemplate: MongoTemplate,
) : RefreshTokenRepository {
    override fun save(token: RefreshToken): RefreshToken = repository.save(token.toDocument()).toDomain()

    override fun findByTokenHash(tokenHash: String): RefreshToken? = repository.findByTokenHash(tokenHash)?.toDomain()

    override fun findAllByFamilyId(familyId: String): List<RefreshToken> =
        repository.findAllByFamilyId(familyId).map(RefreshTokenDocument::toDomain)

    override fun revokeIfActive(
        tokenHash: String,
        revokedAt: Instant,
        replacementTokenHash: String?,
    ): Boolean {
        val query = Query.query(Criteria.where("tokenHash").`is`(tokenHash).and("revokedAt").`is`(null))
        val update = Update().set("revokedAt", revokedAt.toMongoPrecision())
        replacementTokenHash?.let { update.set("replacementTokenHash", it) }
        return mongoTemplate.updateFirst(query, update, RefreshTokenDocument::class.java).modifiedCount == 1L
    }

    override fun revokeFamily(
        familyId: String,
        revokedAt: Instant,
    ): Long {
        val query = Query.query(Criteria.where("familyId").`is`(familyId).and("revokedAt").`is`(null))
        val update = Update().set("revokedAt", revokedAt.toMongoPrecision())
        return mongoTemplate.updateMulti(query, update, RefreshTokenDocument::class.java).modifiedCount
    }
}

private fun RefreshToken.toDocument() =
    RefreshTokenDocument(
        id = id,
        tokenHash = tokenHash,
        familyId = familyId,
        userId = userId,
        expiresAt = expiresAt.toMongoPrecision(),
        revokedAt = revokedAt?.toMongoPrecision(),
        replacementTokenHash = replacementTokenHash,
        createdAt = createdAt?.toMongoPrecision(),
        updatedAt = updatedAt?.toMongoPrecision(),
    )

private fun RefreshTokenDocument.toDomain() =
    RefreshToken(
        id = id,
        tokenHash = tokenHash,
        familyId = familyId,
        userId = userId,
        expiresAt = expiresAt.toMongoPrecision(),
        revokedAt = revokedAt?.toMongoPrecision(),
        replacementTokenHash = replacementTokenHash,
        createdAt = createdAt?.toMongoPrecision(),
        updatedAt = updatedAt?.toMongoPrecision(),
    )
