package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.TraceFlags
import io.embrace.opentelemetry.kotlin.tracing.model.TraceState

@ExperimentalApi
class FakeSpanContext : SpanContext {
    override val isRemote: Boolean = false
    override val isValid: Boolean = false
    override val spanId: String = ""
    override val traceFlags: TraceFlags
        get() = throw UnsupportedOperationException()
    override val traceId: String = ""
    override val traceState: TraceState
        get() = throw UnsupportedOperationException()
}
