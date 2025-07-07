package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerProvider
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.TracerProvider
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalApi::class)
class EmbOtelJavaTracerProvider(
    private val sdkTracerProvider: TracerProvider,
    private val spanService: SpanService,
    private val clock: Clock,
) : OtelJavaTracerProvider {

    private val tracers = ConcurrentHashMap<TracerKey, OtelJavaTracer>()

    override fun get(instrumentationScopeName: String): OtelJavaTracer = tracerBuilder(instrumentationScopeName).build()

    override fun get(instrumentationScopeName: String, instrumentationScopeVersion: String): OtelJavaTracer =
        tracerBuilder(instrumentationScopeName).setInstrumentationVersion(instrumentationScopeVersion).build()

    override fun tracerBuilder(instrumentationScopeName: String): OtelJavaTracerBuilder {
        return EmbOtelJavaTracerBuilder(
            instrumentationScopeName = instrumentationScopeName,
            tracerSupplier = ::getTracer
        )
    }

    private fun getTracer(key: TracerKey): OtelJavaTracer {
        return tracers[key]
            ?: synchronized(tracers) {
                return tracers[key] ?: createTracer(key)
            }
    }

    private fun createTracer(key: TracerKey): OtelJavaTracer {
        val tracer = EmbOtelJavaTracer(
            sdkTracer = buildSdkTracer(key),
            spanService = spanService,
            clock = clock
        )
        tracers[key] = tracer
        return tracer
    }

    private fun buildSdkTracer(key: TracerKey): Tracer {
        return sdkTracerProvider.getTracer(
            name = key.instrumentationScopeName,
            version = key.instrumentationScopeVersion,
            schemaUrl = key.schemaUrl
        )
    }
}
