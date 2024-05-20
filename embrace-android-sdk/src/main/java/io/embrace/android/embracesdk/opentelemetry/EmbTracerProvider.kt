package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.spans.TracerCache
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder
import io.opentelemetry.api.trace.TracerProvider

internal class EmbTracerProvider(
    private val sdkTracerProvider: TracerProvider,
    private val tracerCache: TracerCache
) : TracerProvider {
    override fun get(instrumentationScopeName: String): Tracer = tracerBuilder(instrumentationScopeName).build()

    override fun get(instrumentationScopeName: String, instrumentationScopeVersion: String): Tracer =
        tracerBuilder(instrumentationScopeName).setInstrumentationVersion(instrumentationScopeVersion).build()

    override fun tracerBuilder(instrumentationScopeName: String): TracerBuilder {
        return EmbTracerBuilder(
            instrumentationScopeName = instrumentationScopeName,
            sdkTracerProvider = sdkTracerProvider,
            tracerCache = tracerCache,
        )
    }
}
