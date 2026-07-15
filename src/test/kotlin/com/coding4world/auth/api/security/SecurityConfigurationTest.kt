package com.coding4world.auth.api.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigurationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val springSecurityFilterChain: FilterChainProxy,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurityFilterChain)
                .build()
    }

    @Test
    fun `health endpoint is public`() {
        mockMvc.get("/actuator/health")
            .andExpect {
                status { isServiceUnavailable() }
            }
    }

    @Test
    fun `info endpoint is public`() {
        mockMvc.get("/actuator/info")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `login and refresh endpoints are public`() {
        mockMvc.get("/api/v1/auth/login")
            .andExpect {
                status { isMethodNotAllowed() }
            }
        mockMvc.get("/api/v1/auth/refresh")
            .andExpect {
                status { isMethodNotAllowed() }
            }
    }

    @Test
    fun `jwks endpoint is public`() {
        mockMvc.get("/api/v1/auth/jwks")
            .andExpect {
                status { isOk() }
                jsonPath("$.keys[0].kid") { value("auth-api-rs256") }
                jsonPath("$.keys[0].d") { doesNotExist() }
            }
    }

    @Test
    fun `api endpoints require authentication by default`() {
        mockMvc.get("/api/v1/bootstrap-probe")
            .andExpect {
                status { isUnauthorized() }
                content { contentTypeCompatibleWith("application/problem+json") }
                jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            }
    }

    @Test
    fun `admin endpoints require administrator role`() {
        mockMvc.get("/api/v1/admin/users")
            .andExpect {
                status { isUnauthorized() }
                content { contentTypeCompatibleWith("application/problem+json") }
                jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            }

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/admin/users")
                .with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))),
        ).andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }
}
