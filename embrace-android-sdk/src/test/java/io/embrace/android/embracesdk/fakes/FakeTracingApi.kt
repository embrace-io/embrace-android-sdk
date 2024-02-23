package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi

internal class FakeTracingApi : TracingApi {

    val createdSpans = mutableListOf<String>()

    override fun createSpan(name: String, parent: EmbraceSpan?): EmbraceSpan? {
        createdSpans.add(name)
        return null
    }

    override fun startSpan(name: String, parent: EmbraceSpan?, startTimeMs: Long?): EmbraceSpan? {
        TODO("Not yet implemented")
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
        code: () -> T
    ): T {
        TODO("Not yet implemented")
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun getSpan(spanId: String): EmbraceSpan? {
        TODO("Not yet implemented")
    }

    @Deprecated("Not required. Use Embrace.isStarted() to know when the full tracing API is available")
    override fun isTracingAvailable(): Boolean {
        TODO("Not yet implemented")
    }
}
