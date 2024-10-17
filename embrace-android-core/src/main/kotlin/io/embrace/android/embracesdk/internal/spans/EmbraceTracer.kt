package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi

class EmbraceTracer(
    private val clock: Clock,
    private val spanService: SpanService,
) : TracingApi {
    override fun createSpan(name: String, parent: EmbraceSpan?): EmbraceSpan? =
        spanService.createSpan(
            name = name,
            parent = parent,
            internal = false,
            private = false,
        )

    override fun startSpan(name: String, parent: EmbraceSpan?, startTimeMs: Long?): EmbraceSpan? =
        spanService.startSpan(
            name = name,
            parent = parent,
            startTimeMs = startTimeMs?.normalizeTimestampAsMillis(),
            internal = false,
            private = false,
        )

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
        code: () -> T
    ): T = spanService.recordSpan(
        name = name,
        parent = parent,
        internal = false,
        private = false,
        attributes = attributes ?: emptyMap(),
        events = events ?: emptyList(),
        code = code
    )

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?
    ): Boolean = spanService.recordCompletedSpan(
        name = name,
        startTimeMs = startTimeMs.normalizeTimestampAsMillis(),
        endTimeMs = endTimeMs.normalizeTimestampAsMillis(),
        parent = parent,
        internal = false,
        private = false,
        attributes = attributes ?: emptyMap(),
        events = events ?: emptyList(),
        errorCode = errorCode
    )

    override fun getSpan(spanId: String): EmbraceSpan? = spanService.getSpan(spanId = spanId)

    /**
     * Return the current time in millis for the clock instance used by the Embrace SDK. This should be used to obtain the time
     * in used for [recordCompletedSpan] so the timestamps will be in sync with those used by the SDK when a time is implicitly recorded.
     */
    fun getSdkCurrentTimeMs(): Long = clock.now()
}
