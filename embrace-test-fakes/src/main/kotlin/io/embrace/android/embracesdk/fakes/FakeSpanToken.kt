package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.SpanToken
import io.embrace.android.embracesdk.spans.EmbraceSpan

class FakeSpanToken(private val span: EmbraceSpan) : SpanToken {
    override fun stop(endTimeMs: Long?) {
        span.stop(endTimeMs = endTimeMs)
    }
}
