package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

internal class FakeSpanService : SpanService {

    val createdSpans = mutableListOf<String>()

    override fun initializeService(sdkInitStartTimeNanos: Long) {
    }

    override fun initialized(): Boolean = true

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean
    ): EmbraceSpan? {
        createdSpans.add(name)
        return null
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        code: () -> T
    ): T {
        return code()
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?
    ): Boolean = true

    override fun getSpan(spanId: String): EmbraceSpan? = null
}
