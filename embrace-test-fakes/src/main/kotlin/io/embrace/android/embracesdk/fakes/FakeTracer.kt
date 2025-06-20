package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer

class FakeTracer(
    val tracerKey: TracerKey = TracerKey(instrumentationScopeName = "fake-scope"),
) : OtelJavaTracer {
    override fun spanBuilder(spanName: String): FakeSpanBuilder = FakeSpanBuilder(spanName, tracerKey)
}
