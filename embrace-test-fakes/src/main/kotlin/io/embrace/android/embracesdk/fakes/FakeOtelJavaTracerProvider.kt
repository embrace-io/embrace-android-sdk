package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerProvider

class FakeOtelJavaTracerProvider : OtelJavaTracerProvider {
    override fun get(instrumentationScopeName: String): OtelJavaTracer = tracerBuilder(instrumentationScopeName).build()

    override fun get(instrumentationScopeName: String, instrumentationScopeVersion: String): OtelJavaTracer =
        tracerBuilder(instrumentationScopeName).setInstrumentationVersion(instrumentationScopeVersion).build()

    override fun tracerBuilder(instrumentationScopeName: String): FakeTracerBuilder =
        FakeTracerBuilder(instrumentationScopeName)
}
