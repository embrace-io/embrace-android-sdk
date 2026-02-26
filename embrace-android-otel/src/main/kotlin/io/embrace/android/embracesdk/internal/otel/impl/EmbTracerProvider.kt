package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.sdk.ApiKey
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.TracerProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
@OptIn(ExperimentalApi::class)
class EmbTracerProvider(
    private val impl: OpenTelemetry,
    private val spanService: SpanService,
    private val clock: Clock,
    private val useKotlinSdk: Boolean
) : TracerProvider {

    private val tracers = ConcurrentHashMap<ApiKey, Tracer>()

    override fun getTracer(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ): Tracer {
        val key = ApiKey(
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

    private fun createTracer(key: ApiKey): Tracer {
        val tracerImpl = impl.tracerProvider.getTracer(
            name = key.instrumentationScopeName,
            version = key.instrumentationScopeVersion,
            schemaUrl = key.schemaUrl
        )
        val tracer = EmbTracer(
            impl = tracerImpl,
            spanService = spanService,
            clock = clock,
            openTelemetry = impl,
            useKotlinSdk = useKotlinSdk
        )
        tracers[key] = tracer
        return tracer
    }
}
