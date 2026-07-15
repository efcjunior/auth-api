package com.coding4world.auth.api.security

import com.coding4world.auth.api.shared.config.AuthApiProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        properties: AuthApiProperties,
        securityProblems: SecurityProblemWriter,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorization ->
                authorization.requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                authorization.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                val documentation = arrayOf("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                if (properties.openApi.public) {
                    authorization.requestMatchers(*documentation).permitAll()
                } else {
                    authorization.requestMatchers(*documentation).authenticated()
                }
                authorization
                    .requestMatchers("/api/v1/**").authenticated()
                    .anyRequest().denyAll()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, _ ->
                    securityProblems.write(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required",
                        "A valid access token is required",
                        "AUTHENTICATION_REQUIRED",
                    )
                }
                exceptions.accessDeniedHandler { _, response, _ ->
                    securityProblems.write(
                        response,
                        HttpStatus.FORBIDDEN,
                        "Access denied",
                        "You do not have permission to access this resource",
                        "ACCESS_DENIED",
                    )
                }
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
        return http.build()
    }

    private fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val authoritiesConverter = JwtGrantedAuthoritiesConverter()
        authoritiesConverter.setAuthoritiesClaimName("roles")
        authoritiesConverter.setAuthorityPrefix("ROLE_")
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authoritiesConverter)
        }
    }
}
