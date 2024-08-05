package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.opentelemetry.TracerKey
import io.opentelemetry.api.trace.Tracer

public class FakeTracer(
    public val tracerKey: TracerKey = TracerKey(instrumentationScopeName = "fake-scope")
) : Tracer {
    override fun spanBuilder(spanName: String): FakeSpanBuilder = FakeSpanBuilder(spanName, tracerKey)
}
