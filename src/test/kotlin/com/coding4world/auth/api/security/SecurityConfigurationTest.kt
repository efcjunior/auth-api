package com.coding4world.auth.api.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
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
    fun `api endpoints require authentication by default`() {
        mockMvc.get("/api/v1/bootstrap-probe")
            .andExpect {
                status { isUnauthorized() }
                content { contentTypeCompatibleWith("application/problem+json") }
                jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            }
    }
}
