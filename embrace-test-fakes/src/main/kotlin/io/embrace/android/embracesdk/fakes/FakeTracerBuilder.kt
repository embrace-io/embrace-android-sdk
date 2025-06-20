package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerBuilder

class FakeTracerBuilder(
    val instrumentationScopeName: String,
) : OtelJavaTracerBuilder {

    var scopeVersion: String? = null
    var schema: String? = null

    override fun setInstrumentationVersion(instrumentationScopeVersion: String): OtelJavaTracerBuilder {
        scopeVersion = instrumentationScopeVersion
        return this
    }

    override fun setSchemaUrl(schemaUrl: String): OtelJavaTracerBuilder {
        schema = schemaUrl
        return this
    }

    override fun build(): OtelJavaTracer =
        FakeTracer(
            TracerKey(
                instrumentationScopeName = instrumentationScopeName,
                instrumentationScopeVersion = scopeVersion,
                schemaUrl = schema
            )
        )
}
