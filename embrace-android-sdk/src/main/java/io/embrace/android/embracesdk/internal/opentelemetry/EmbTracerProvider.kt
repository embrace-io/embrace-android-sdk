package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.internal.spans.SpanService
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.ConcurrentHashMap

internal class EmbTracerProvider(
    private val sdkTracerProvider: TracerProvider,
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

    private fun buildSdkTracer(key: TracerKey): Tracer {
        val builder = sdkTracerProvider.tracerBuilder(key.instrumentationScopeName)
        key.instrumentationScopeVersion?.apply {
            builder.setInstrumentationVersion(this)
        }
        key.schemaUrl?.apply {
            builder.setSchemaUrl(this)
        }
        return builder.build()
    }
}
