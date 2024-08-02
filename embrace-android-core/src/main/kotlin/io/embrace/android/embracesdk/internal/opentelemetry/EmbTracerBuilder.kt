package io.embrace.android.embracesdk.internal.opentelemetry

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder

public class EmbTracerBuilder(
    instrumentationScopeName: String,
    private val tracerSupplier: (tracerKey: TracerKey) -> Tracer
) : TracerBuilder {

    private val tracerKey: TracerKey = TracerKey(instrumentationScopeName = instrumentationScopeName)

    override fun setSchemaUrl(schemaUrl: String): TracerBuilder {
        tracerKey.schemaUrl = schemaUrl
        return this
    }

    override fun setInstrumentationVersion(instrumentationScopeVersion: String): TracerBuilder {
        tracerKey.instrumentationScopeVersion = instrumentationScopeVersion
        return this
    }

    override fun build(): Tracer = tracerSupplier(tracerKey)
}
