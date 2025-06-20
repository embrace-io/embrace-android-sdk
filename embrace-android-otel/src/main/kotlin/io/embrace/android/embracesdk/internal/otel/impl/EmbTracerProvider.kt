package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaClock
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerProvider
import java.util.concurrent.ConcurrentHashMap

class EmbTracerProvider(
    private val sdkTracerProvider: OtelJavaTracerProvider,
    private val spanService: SpanService,
    private val clock: OtelJavaClock,
) : OtelJavaTracerProvider {

    private val tracers = ConcurrentHashMap<TracerKey, OtelJavaTracer>()

    override fun get(instrumentationScopeName: String): OtelJavaTracer = tracerBuilder(instrumentationScopeName).build()

    override fun get(instrumentationScopeName: String, instrumentationScopeVersion: String): OtelJavaTracer =
        tracerBuilder(instrumentationScopeName).setInstrumentationVersion(instrumentationScopeVersion).build()

    override fun tracerBuilder(instrumentationScopeName: String): OtelJavaTracerBuilder {
        return EmbTracerBuilder(
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
        val tracer = EmbTracer(
            sdkTracer = buildSdkTracer(key),
            spanService = spanService,
            clock = clock
        )
        tracers[key] = tracer
        return tracer
    }

    private fun buildSdkTracer(key: TracerKey): OtelJavaTracer {
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
