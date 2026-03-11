package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.tracing.model.SpanContext
import io.opentelemetry.kotlin.tracing.model.TraceFlags
import io.opentelemetry.kotlin.tracing.model.TraceState

class FakeSpanContext : SpanContext {
    override val isRemote: Boolean = false
    override val isValid: Boolean = false
    override val spanId: String = ""
    override val traceFlags: TraceFlags = FakeTraceFlags()
    override val traceId: String = ""
    override val traceState: TraceState = FakeTraceState()
    override val spanIdBytes: ByteArray = spanId.toByteArray()
    override val traceIdBytes: ByteArray = traceId.toByteArray()
}
