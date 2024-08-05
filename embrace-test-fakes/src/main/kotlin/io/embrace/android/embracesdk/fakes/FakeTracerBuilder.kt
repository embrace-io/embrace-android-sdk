package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.opentelemetry.TracerKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder

public class FakeTracerBuilder(
    public val instrumentationScopeName: String
) : TracerBuilder {

    public var scopeVersion: String? = null
    public var schema: String? = null

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
