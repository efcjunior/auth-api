package com.coding4world.auth.api.shared.ratelimit

import com.coding4world.auth.api.shared.config.AuthApiProperties
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.ConsumptionProbe
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitBucketRegistry {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryConsume(
        policy: RateLimitPolicy,
        identity: String,
        configuration: AuthApiProperties.RateLimit.Policy,
    ): ConsumptionProbe =
        buckets
            .computeIfAbsent("$policy:$identity") { newBucket(configuration) }
            .tryConsumeAndReturnRemaining(1)

    private fun newBucket(configuration: AuthApiProperties.RateLimit.Policy): Bucket =
        Bucket
            .builder()
            .addLimit(
                Bandwidth
                    .builder()
                    .capacity(configuration.capacity)
                    .refillIntervally(configuration.capacity, configuration.refillPeriod)
                    .build(),
            )
            .build()
}
