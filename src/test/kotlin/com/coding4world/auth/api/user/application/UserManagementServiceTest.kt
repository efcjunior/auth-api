package com.coding4world.auth.api.user.application

import com.coding4world.auth.api.shared.domain.PageResult
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.model.UserRole
import com.coding4world.auth.api.user.domain.port.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder

class UserManagementServiceTest {
    private val repository = InMemoryManagedUserRepository()
    private val passwordEncoder = ManagedUserPasswordEncoder()
    private val service = UserManagementService(repository, passwordEncoder)

    @Test
    fun `create normalizes email hashes password and persists user`() {
        val user = service.create(" USER@EXAMPLE.COM ", "strong-password", setOf(UserRole.USER), true)

        assertThat(user.id).isEqualTo("user-1")
        assertThat(user.normalizedEmail).isEqualTo("user@example.com")
        assertThat(user.passwordHash).isEqualTo("encoded:strong-password")
        assertThat(user.roles).containsExactly(UserRole.USER)
        assertThat(user.enabled).isTrue()
    }

    @Test
    fun `create rejects duplicate email and empty roles`() {
        service.create("user@example.com", "strong-password", setOf(UserRole.USER), true)

        assertThrows<UserAlreadyExistsException> {
            service.create(" USER@EXAMPLE.COM ", "another-password", setOf(UserRole.USER), true)
        }
        assertThrows<InvalidUserUpdateException> {
            service.create("another@example.com", "strong-password", emptySet(), true)
        }
    }

    @Test
    fun `list returns paged users ordered by email`() {
        service.create("zeta@example.com", "strong-password", setOf(UserRole.USER), true)
        service.create("alpha@example.com", "strong-password", setOf(UserRole.ADMIN), true)

        val result = service.list(page = 0, size = 1)

        assertThat(result.content).extracting<String> { it.normalizedEmail }.containsExactly("alpha@example.com")
        assertThat(result.totalElements).isEqualTo(2)
        assertThat(result.totalPages).isEqualTo(2)
    }

    @Test
    fun `get returns user or fails with not found`() {
        val user = service.create("user@example.com", "strong-password", setOf(UserRole.USER), true)

        assertThat(service.get(requireNotNull(user.id))).isEqualTo(user)
        assertThrows<UserNotFoundException> { service.get("missing") }
    }

    @Test
    fun `update changes roles and enabled state`() {
        val user = service.create("user@example.com", "strong-password", setOf(UserRole.USER), true)

        val updated = service.update(requireNotNull(user.id), setOf(UserRole.ADMIN), false)

        assertThat(updated.roles).containsExactly(UserRole.ADMIN)
        assertThat(updated.enabled).isFalse()
        assertThat(updated.passwordHash).isEqualTo("encoded:strong-password")
    }

    @Test
    fun `update rejects empty body and empty roles`() {
        val user = service.create("user@example.com", "strong-password", setOf(UserRole.USER), true)

        assertThrows<InvalidUserUpdateException> { service.update(requireNotNull(user.id), null, null) }
        assertThrows<InvalidUserUpdateException> { service.update(requireNotNull(user.id), emptySet(), null) }
    }
}

private class ManagedUserPasswordEncoder : PasswordEncoder {
    override fun encode(rawPassword: CharSequence?): String? = rawPassword?.let { "encoded:$it" }

    override fun matches(
        rawPassword: CharSequence?,
        encodedPassword: String?,
    ): Boolean = rawPassword != null && encode(rawPassword) == encodedPassword
}

private class InMemoryManagedUserRepository : UserRepository {
    private val users = linkedMapOf<String, User>()

    override fun save(user: User): User {
        val saved = user.copy(id = user.id ?: "user-${users.size + 1}")
        users[requireNotNull(saved.id)] = saved
        return saved
    }

    override fun findAll(
        page: Int,
        size: Int,
    ): PageResult<User> {
        val ordered = users.values.sortedBy { it.normalizedEmail }
        val fromIndex = page * size
        val content = if (fromIndex >= ordered.size) emptyList() else ordered.drop(fromIndex).take(size)
        return PageResult(
            content = content,
            page = page,
            size = size,
            totalElements = ordered.size.toLong(),
            totalPages = if (ordered.isEmpty()) 0 else ((ordered.size - 1) / size) + 1,
        )
    }

    override fun findByNormalizedEmail(normalizedEmail: String): User? =
        users.values.firstOrNull { it.normalizedEmail == normalizedEmail }

    override fun findById(id: String): User? = users[id]

    override fun existsByRole(role: UserRole): Boolean = users.values.any { role in it.roles }
}
