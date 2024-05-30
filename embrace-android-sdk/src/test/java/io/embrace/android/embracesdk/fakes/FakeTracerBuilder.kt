package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.opentelemetry.TracerKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder

internal class FakeTracerBuilder(
    val instrumentationScopeName: String
) : TracerBuilder {

    var scopeVersion: String? = null
    var schema: String? = null

    override fun setInstrumentationVersion(instrumentationScopeVersion: String): TracerBuilder {
        scopeVersion = instrumentationScopeVersion
        return this
    }

    override fun setSchemaUrl(schemaUrl: String): TracerBuilder {
        schema = schemaUrl
        return this
    }

    override fun build(): Tracer =
        FakeTracer(
            TracerKey(
                instrumentationScopeName = instrumentationScopeName,
                instrumentationScopeVersion = scopeVersion,
                schemaUrl = schema
            )
        )
}
