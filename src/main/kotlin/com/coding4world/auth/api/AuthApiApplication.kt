package com.coding4world.auth.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class AuthApiApplication

fun main(args: Array<String>) {
    runApplication<AuthApiApplication>(*args)
}
