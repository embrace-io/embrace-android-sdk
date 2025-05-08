package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalApi::class)
class EmbTracerProvider(
    private val sdkTracerProvider: io.embrace.opentelemetry.kotlin.tracing.TracerProvider,
    private val spanService: SpanService,
    private val clock: Clock,
) : TracerProvider {

    private val tracers = ConcurrentHashMap<TracerKey, Tracer>()

    override fun get(instrumentationScopeName: String): Tracer = tracerBuilder(instrumentationScopeName).build()

    override fun get(instrumentationScopeName: String, instrumentationScopeVersion: String): Tracer =
        tracerBuilder(instrumentationScopeName).setInstrumentationVersion(instrumentationScopeVersion).build()

    override fun tracerBuilder(instrumentationScopeName: String): TracerBuilder {
        return EmbTracerBuilder(
            instrumentationScopeName = instrumentationScopeName,
            tracerSupplier = ::getTracer
        )
    }

    private fun getTracer(key: TracerKey): Tracer {
        return tracers[key]
            ?: synchronized(tracers) {
                return tracers[key] ?: createTracer(key)
            }
    }

    private fun createTracer(key: TracerKey): Tracer {
        val tracer = EmbTracer(
            sdkTracer = buildSdkTracer(key),
            spanService = spanService,
            clock = clock
        )
        tracers[key] = tracer
        return tracer
    }

    private fun buildSdkTracer(key: TracerKey): io.embrace.opentelemetry.kotlin.tracing.Tracer {
        return sdkTracerProvider.getTracer(
            name = key.instrumentationScopeName,
            version = key.instrumentationScopeVersion,
            schemaUrl = key.schemaUrl
        )
    }
}
