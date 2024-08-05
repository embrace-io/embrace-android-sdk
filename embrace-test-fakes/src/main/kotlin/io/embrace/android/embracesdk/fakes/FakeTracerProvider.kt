package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider

public class FakeTracerProvider : TracerProvider {
    override fun get(instrumentationScopeName: String): Tracer = tracerBuilder(instrumentationScopeName).build()

    override fun get(instrumentationScopeName: String, instrumentationScopeVersion: String): Tracer =
        tracerBuilder(instrumentationScopeName).setInstrumentationVersion(instrumentationScopeVersion).build()

    override fun tracerBuilder(instrumentationScopeName: String): FakeTracerBuilder = FakeTracerBuilder(instrumentationScopeName)
}
