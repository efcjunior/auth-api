package com.coding4world.auth.api.shared.web

import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

@Component
class ApiProblemFactory {
    fun create(
        status: HttpStatus,
        title: String,
        detail: String,
        code: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            type = URI.create("https://auth.coding4world.com/problems/${code.lowercase().replace('_', '-')}")
            this.title = title
            setProperty("code", code)
            setProperty("traceId", currentTraceId())
        }

    fun currentTraceId(): String = MDC.get(TraceIdFilter.TRACE_ID) ?: UUID.randomUUID().toString()
}
