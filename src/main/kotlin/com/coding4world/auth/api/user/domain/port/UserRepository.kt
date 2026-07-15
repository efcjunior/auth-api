package com.coding4world.auth.api.user.domain.port

import com.coding4world.auth.api.shared.domain.PageResult
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.model.UserRole

interface UserRepository {
    fun save(user: User): User

    fun findAll(
        page: Int,
        size: Int,
    ): PageResult<User>

    fun findByNormalizedEmail(normalizedEmail: String): User?

    fun findById(id: String): User?

    fun existsByRole(role: UserRole): Boolean
}
