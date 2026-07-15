package com.coding4world.auth.api.security

import com.coding4world.auth.api.shared.web.ApiProblemFactory
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class SecurityProblemWriter(
    private val objectMapper: ObjectMapper,
    private val problems: ApiProblemFactory,
) {
    fun write(
        response: HttpServletResponse,
        status: HttpStatus,
        title: String,
        detail: String,
        code: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        val problem = problems.create(status, title, detail, code)
        val body =
            linkedMapOf<String, Any?>(
                "type" to problem.type.toString(),
                "title" to problem.title,
                "status" to status.value(),
                "detail" to problem.detail,
                "code" to code,
                "traceId" to problems.currentTraceId(),
            )
        objectMapper.writeValue(response.outputStream, body)
    }
}
