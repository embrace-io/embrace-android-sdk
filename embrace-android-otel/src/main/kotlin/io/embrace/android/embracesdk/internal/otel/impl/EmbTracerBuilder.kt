package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerBuilder

internal class EmbTracerBuilder(
    instrumentationScopeName: String,
    private val tracerSupplier: (tracerKey: TracerKey) -> OtelJavaTracer,
) : OtelJavaTracerBuilder {

    private val tracerKey: TracerKey = TracerKey(instrumentationScopeName = instrumentationScopeName)

    override fun setSchemaUrl(schemaUrl: String): OtelJavaTracerBuilder {
        tracerKey.schemaUrl = schemaUrl
        return this
    }

    override fun setInstrumentationVersion(instrumentationScopeVersion: String): OtelJavaTracerBuilder {
        tracerKey.instrumentationScopeVersion = instrumentationScopeVersion
        return this
    }

    override fun build(): OtelJavaTracer = tracerSupplier(tracerKey)
}
