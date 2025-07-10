package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.TracerProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
@OptIn(ExperimentalApi::class)
class EmbTracerProvider(
    private val impl: TracerProvider,
    private val spanService: SpanService,
    private val clock: Clock,
) : TracerProvider {

    private val tracers = ConcurrentHashMap<TracerKey, Tracer>()

    override fun getTracer(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: AttributeContainer.() -> Unit,
    ): Tracer {
        val key = TracerKey(
            instrumentationScopeName = name,
            instrumentationScopeVersion = version,
            schemaUrl = schemaUrl
        )

        val tracer = (
            tracers[key]
                ?: synchronized(tracers) {
                    return tracers[key] ?: createTracer(key)
                }
            )
        return tracer
    }

    private fun createTracer(key: TracerKey): Tracer {
        val impl = impl.getTracer(
            name = key.instrumentationScopeName,
            version = key.instrumentationScopeVersion,
            schemaUrl = key.schemaUrl
        )
        val tracer = EmbTracer(
            impl = impl,
            spanService = spanService,
            clock = clock
        )
        tracers[key] = tracer
        return tracer
    }
}
