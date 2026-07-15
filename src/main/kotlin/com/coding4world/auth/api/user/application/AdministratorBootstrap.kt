package com.coding4world.auth.api.user.application

import com.coding4world.auth.api.shared.config.AuthApiProperties
import com.coding4world.auth.api.user.domain.model.User
import com.coding4world.auth.api.user.domain.model.UserRole
import com.coding4world.auth.api.user.domain.port.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class AdministratorBootstrap(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val properties: AuthApiProperties,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun createInitialAdministrator() {
        val configuration = properties.security.bootstrapAdmin
        if (configuration.email.isBlank() && configuration.password.isBlank()) return
        require(configuration.email.isNotBlank() && configuration.password.isNotBlank()) {
            "Both BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD must be configured"
        }
        require(configuration.password.length >= MINIMUM_PASSWORD_LENGTH) {
            "BOOTSTRAP_ADMIN_PASSWORD must contain at least $MINIMUM_PASSWORD_LENGTH characters"
        }
        if (userRepository.existsByRole(UserRole.ADMIN)) return

        userRepository.save(
            User(
                normalizedEmail = configuration.email.trim().lowercase(Locale.ROOT),
                passwordHash = requireNotNull(passwordEncoder.encode(configuration.password)),
                roles = setOf(UserRole.ADMIN),
                enabled = true,
            ),
        )
        logger.info("Created the initial administrator account")
    }

    private companion object {
        const val MINIMUM_PASSWORD_LENGTH = 12
        val logger = LoggerFactory.getLogger(AdministratorBootstrap::class.java)
    }
}
