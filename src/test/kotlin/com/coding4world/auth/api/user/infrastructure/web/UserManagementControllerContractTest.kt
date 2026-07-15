package com.coding4world.auth.api.user.infrastructure.web

import com.coding4world.auth.api.shared.domain.PageResult
import com.coding4world.auth.api.shared.web.ApiExceptionHandler
import com.coding4world.auth.api.shared.web.ApiProblemFactory
import com.coding4world.auth.api.shared.web.TraceIdFilter
import com.coding4world.auth.api.user.application.UserManagementService
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.model.UserRole
import com.coding4world.auth.api.user.domain.port.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class UserManagementControllerContractTest {
    private val repository = ContractUserRepository()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        repository.clear()
        val controller = UserManagementController(UserManagementService(repository, ContractPasswordEncoder()))
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(ApiExceptionHandler(ApiProblemFactory()))
                .addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(TraceIdFilter())
                .build()
    }

    @Test
    fun `administrator creates a user without exposing password hash`() {
        mockMvc.perform(
            post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"email":"USER@EXAMPLE.COM","password":"strong-password","roles":["USER"]}""",
                ),
        ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/v1/admin/users/user-1"))
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.roles[0]").value("USER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
    }

    @Test
    fun `administrator lists users without exposing password hashes`() {
        repository.save(
            User(
                normalizedEmail = "second@example.com",
                passwordHash = "encoded",
                roles = setOf(UserRole.USER),
                enabled = true,
            ),
        )
        repository.save(
            User(
                normalizedEmail = "first@example.com",
                passwordHash = "encoded",
                roles = setOf(UserRole.ADMIN),
                enabled = true,
            ),
        )

        mockMvc.perform(get("/api/v1/admin/users?page=0&size=1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].email").value("first@example.com"))
            .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
    }

    @Test
    fun `administrator gets an existing user`() {
        repository.save(
            User(
                id = "user-existing",
                normalizedEmail = "user@example.com",
                passwordHash = "encoded",
                roles = setOf(UserRole.USER),
                enabled = true,
            ),
        )

        mockMvc.perform(get("/api/v1/admin/users/user-existing"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
    }

    @Test
    fun `administrator can disable an existing user`() {
        repository.save(
            User(
                id = "user-existing",
                normalizedEmail = "user@example.com",
                passwordHash = "encoded",
                roles = setOf(UserRole.USER),
                enabled = true,
            ),
        )

        mockMvc.perform(
            patch("/api/v1/admin/users/user-existing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
    }

    @Test
    fun `missing user returns problem details`() {
        mockMvc.perform(get("/api/v1/admin/users/missing"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
    }

    @Test
    fun `duplicate email returns conflict problem details`() {
        repository.save(
            User(
                normalizedEmail = "user@example.com",
                passwordHash = "encoded",
                roles = setOf(UserRole.USER),
                enabled = true,
            ),
        )

        mockMvc.perform(
            post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"USER@EXAMPLE.COM","password":"strong-password","roles":["USER"]}"""),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"))
    }

    @Test
    fun `short password returns validation problem details`() {
        mockMvc.perform(
            post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com","password":"short","roles":["USER"]}"""),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("password"))
    }

    @Test
    fun `empty patch returns invalid request problem`() {
        mockMvc.perform(
            patch("/api/v1/admin/users/user-existing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }
}

private class ContractPasswordEncoder : PasswordEncoder {
    override fun encode(rawPassword: CharSequence?): String? = rawPassword?.let { "encoded:$it" }

    override fun matches(
        rawPassword: CharSequence?,
        encodedPassword: String?,
    ): Boolean = rawPassword != null && encode(rawPassword) == encodedPassword
}

private class ContractUserRepository : UserRepository {
    private val users = linkedMapOf<String, User>()

    fun clear() {
        users.clear()
    }

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
