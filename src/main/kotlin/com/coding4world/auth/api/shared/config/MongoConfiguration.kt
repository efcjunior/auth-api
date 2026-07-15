package com.coding4world.auth.api.shared.config

import com.coding4world.auth.api.auth.infrastructure.persistence.RefreshTokenDocument
import com.coding4world.auth.api.user.infrastructure.persistence.UserDocument
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort.Direction
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component
import java.time.Duration

@Configuration
@EnableMongoAuditing
class MongoConfiguration

@Component
@Profile("!test")
class MongoIndexInitializer(
    private val mongoTemplate: MongoTemplate,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun createIndexes() {
        mongoTemplate.indexOps(UserDocument::class.java).apply {
            createIndex(Index().on("normalizedEmail", Direction.ASC).unique().named("uk_users_normalized_email"))
        }

        mongoTemplate.indexOps(RefreshTokenDocument::class.java).apply {
            createIndex(Index().on("tokenHash", Direction.ASC).unique().named("uk_refresh_tokens_token_hash"))
            createIndex(Index().on("familyId", Direction.ASC).named("ix_refresh_tokens_family_id"))
            createIndex(Index().on("expiresAt", Direction.ASC).expire(Duration.ZERO).named("ttl_refresh_tokens_expires_at"))
        }
    }
}
