package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.trace.Tracer

internal class FakeTracer : Tracer {
    override fun spanBuilder(spanName: String): FakeSpanBuilder = FakeSpanBuilder(spanName)
}
