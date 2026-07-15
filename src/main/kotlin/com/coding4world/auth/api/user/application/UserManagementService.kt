package com.coding4world.auth.api.user.application

import com.coding4world.auth.api.shared.domain.PageResult
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.model.UserRole
import com.coding4world.auth.api.user.domain.port.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class UserManagementService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun create(
        email: String,
        password: String,
        roles: Set<UserRole>,
        enabled: Boolean,
    ): User {
        val normalizedEmail = normalizeEmail(email)
        if (userRepository.findByNormalizedEmail(normalizedEmail) != null) {
            throw UserAlreadyExistsException()
        }
        validateRoles(roles)

        return userRepository.save(
            User(
                normalizedEmail = normalizedEmail,
                passwordHash = requireNotNull(passwordEncoder.encode(password)),
                roles = roles,
                enabled = enabled,
            ),
        )
    }

    fun list(
        page: Int,
        size: Int,
    ): PageResult<User> {
        require(page >= 0) { "Page index must not be negative" }
        require(size in 1..MAXIMUM_PAGE_SIZE) { "Page size must be between 1 and $MAXIMUM_PAGE_SIZE" }
        return userRepository.findAll(page, size)
    }

    fun get(userId: String): User = userRepository.findById(userId) ?: throw UserNotFoundException(userId)

    fun update(
        userId: String,
        roles: Set<UserRole>?,
        enabled: Boolean?,
    ): User {
        if (roles == null && enabled == null) {
            throw InvalidUserUpdateException("At least one field must be supplied")
        }
        roles?.let(::validateRoles)
        val existing = get(userId)
        return userRepository.save(
            existing.copy(
                roles = roles ?: existing.roles,
                enabled = enabled ?: existing.enabled,
            ),
        )
    }

    private fun validateRoles(roles: Set<UserRole>) {
        if (roles.isEmpty()) throw InvalidUserUpdateException("At least one role is required")
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private companion object {
        const val MAXIMUM_PAGE_SIZE = 100
    }
}
