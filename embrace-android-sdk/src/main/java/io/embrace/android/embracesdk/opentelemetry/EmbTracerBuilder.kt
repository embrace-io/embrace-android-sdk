package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.spans.TracerCache
import io.embrace.android.embracesdk.internal.spans.TracerKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder
import io.opentelemetry.api.trace.TracerProvider

internal class EmbTracerBuilder(
    instrumentationScopeName: String,
    sdkTracerProvider: TracerProvider,
    private val tracerCache: TracerCache,
) : TracerBuilder {

    private val sdkTracerBuilder = sdkTracerProvider.tracerBuilder(instrumentationScopeName)
    private val tracerKey: TracerKey = TracerKey(instrumentationScopeName = instrumentationScopeName)

    override fun setSchemaUrl(schemaUrl: String): TracerBuilder {
        sdkTracerBuilder.setSchemaUrl(schemaUrl)
        tracerKey.schemaUrl = schemaUrl
        return this
    }

    override fun setInstrumentationVersion(instrumentationScopeVersion: String): TracerBuilder {
        sdkTracerBuilder.setInstrumentationVersion(instrumentationScopeVersion)
        tracerKey.instrumentationScopeVersion = instrumentationScopeVersion
        return this
    }

    override fun build(): Tracer = tracerCache.getTracer(tracerKey) { sdkTracerBuilder.build() }
}
