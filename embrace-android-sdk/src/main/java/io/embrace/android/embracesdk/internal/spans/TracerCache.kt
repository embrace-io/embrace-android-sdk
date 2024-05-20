package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.opentelemetry.EmbTracer
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.ConcurrentHashMap

internal class TracerCache(
    private val spanService: SpanService,
    private val clock: Clock,
) {
    private val tracers = ConcurrentHashMap<TracerKey, Tracer>()

    fun getTracer(tracerKey: TracerKey, tracerSupplier: () -> Tracer): Tracer {
        return tracers[tracerKey]
            ?: synchronized(tracers) {
                return tracers[tracerKey] ?: cacheTracer(tracerKey, tracerSupplier)
            }
    }

    private fun cacheTracer(
        tracerKey: TracerKey,
        tracerSupplier: () -> Tracer
    ): Tracer {
        val newTracer = EmbTracer(
            sdkTracer = tracerSupplier(),
            spanService = spanService,
            clock = clock
        )
        tracers[tracerKey] = newTracer
        return newTracer
    }
}

internal data class TracerKey(
    val instrumentationScopeName: String,
    var instrumentationScopeVersion: String? = null,
    var schemaUrl: String? = null
)
