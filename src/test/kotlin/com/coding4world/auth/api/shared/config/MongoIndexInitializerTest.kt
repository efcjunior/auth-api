package com.coding4world.auth.api.shared.config

import com.coding4world.auth.api.auth.infrastructure.persistence.RefreshTokenDocument
import com.coding4world.auth.api.user.infrastructure.persistence.UserDocument
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexOperations

class MongoIndexInitializerTest {
    @Test
    fun `creates user and refresh token indexes`() {
        val mongoTemplate = Mockito.mock(MongoTemplate::class.java)
        val userIndexes = Mockito.mock(IndexOperations::class.java)
        val refreshTokenIndexes = Mockito.mock(IndexOperations::class.java)
        Mockito.`when`(mongoTemplate.indexOps(UserDocument::class.java)).thenReturn(userIndexes)
        Mockito.`when`(mongoTemplate.indexOps(RefreshTokenDocument::class.java)).thenReturn(refreshTokenIndexes)

        MongoIndexInitializer(mongoTemplate).createIndexes()

        val userIndexCaptor = ArgumentCaptor.forClass(Index::class.java)
        Mockito.verify(userIndexes).createIndex(userIndexCaptor.capture())

        val refreshTokenIndexCaptor = ArgumentCaptor.forClass(Index::class.java)
        Mockito.verify(refreshTokenIndexes, Mockito.times(3)).createIndex(refreshTokenIndexCaptor.capture())
    }
}
