package com.coding4world.auth.api.shared.ratelimit

import com.coding4world.auth.api.shared.config.AuthApiProperties
import com.coding4world.auth.api.shared.web.ApiProblemFactory
import io.github.bucket4j.ConsumptionProbe
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class RateLimitingFilter(
    private val properties: AuthApiProperties,
    private val policyResolver: RateLimitPolicyResolver,
    private val identityResolver: RateLimitIdentityResolver,
    private val bucketRegistry: RateLimitBucketRegistry,
    private val objectMapper: ObjectMapper,
    private val problems: ApiProblemFactory,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.rateLimit.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        val policy = policyResolver.resolve(request)
        if (policy == null) {
            filterChain.doFilter(request, response)
            return
        }

        val identity = identityResolver.resolve(policy, request, properties.rateLimit)
        if (identity == null) {
            filterChain.doFilter(request, response)
            return
        }

        val policyConfiguration = properties.rateLimit.policy(policy)
        val probe = bucketRegistry.tryConsume(policy, identity, policyConfiguration)
        response.applyRateLimitHeaders(policyConfiguration, probe)

        if (probe.isConsumed) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.setHeader("Retry-After", probe.retryAfterSeconds().toString())
        val problem =
            problems.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests",
                "Rate limit exceeded. Retry after ${probe.retryAfterSeconds()} seconds",
                "RATE_LIMIT_EXCEEDED",
            )
        val body =
            linkedMapOf<String, Any?>(
                "type" to problem.type.toString(),
                "title" to problem.title,
                "status" to HttpStatus.TOO_MANY_REQUESTS.value(),
                "detail" to problem.detail,
                "code" to "RATE_LIMIT_EXCEEDED",
                "traceId" to problems.currentTraceId(),
            )
        objectMapper.writeValue(response.outputStream, body)
    }

    private fun HttpServletResponse.applyRateLimitHeaders(
        policy: AuthApiProperties.RateLimit.Policy,
        probe: ConsumptionProbe,
    ) {
        val resetSeconds = probe.resetSeconds(policy.refillPeriod)
        setHeader("RateLimit-Limit", policy.capacity.toString())
        setHeader("RateLimit-Remaining", probe.remainingTokens.coerceAtLeast(0).toString())
        setHeader("RateLimit-Reset", resetSeconds.toString())
        setHeader("X-RateLimit-Limit", policy.capacity.toString())
        setHeader("X-RateLimit-Remaining", probe.remainingTokens.coerceAtLeast(0).toString())
        setHeader("X-RateLimit-Reset", resetSeconds.toString())
    }

    private fun ConsumptionProbe.retryAfterSeconds(): Long = secondsFromNanos(nanosToWaitForRefill).coerceAtLeast(1)

    private fun ConsumptionProbe.resetSeconds(refillPeriod: Duration): Long =
        if (nanosToWaitForRefill > 0) {
            secondsFromNanos(nanosToWaitForRefill)
        } else {
            refillPeriod.seconds
        }

    private fun secondsFromNanos(nanos: Long): Long =
        TimeUnit.NANOSECONDS.toSeconds(nanos) + if (nanos % NANOS_PER_SECOND == 0L) 0 else 1

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
