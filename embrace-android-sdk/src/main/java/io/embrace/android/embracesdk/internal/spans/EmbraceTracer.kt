package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi

internal class EmbraceTracer(
    private val clock: Clock,
    private val spansService: SpansService,
) : TracingApi {
    override fun createSpan(name: String): EmbraceSpan? =
        createSpan(name = name, parent = null)

    override fun createSpan(name: String, parent: EmbraceSpan?): EmbraceSpan? =
        spansService.createSpan(
            name = name,
            parent = parent,
            internal = false
        )

    override fun <T> recordSpan(name: String, code: () -> T): T = recordSpan(name = name, parent = null, code = code)

    override fun <T> recordSpan(name: String, parent: EmbraceSpan?, code: () -> T): T =
        spansService.recordSpan(
            name = name,
            parent = parent,
            internal = false,
            code = code
        )

    override fun recordCompletedSpan(name: String, startTimeNanos: Long, endTimeNanos: Long): Boolean =
        recordCompletedSpan(
            name = name,
            startTimeNanos = startTimeNanos,
            endTimeNanos = endTimeNanos,
            errorCode = null,
            parent = null,
            attributes = null,
            events = null
        )

    override fun recordCompletedSpan(name: String, startTimeNanos: Long, endTimeNanos: Long, errorCode: ErrorCode?): Boolean =
        recordCompletedSpan(
            name = name,
            startTimeNanos = startTimeNanos,
            endTimeNanos = endTimeNanos,
            errorCode = errorCode,
            parent = null,
            attributes = null,
            events = null
        )

    override fun recordCompletedSpan(name: String, startTimeNanos: Long, endTimeNanos: Long, parent: EmbraceSpan?): Boolean =
        recordCompletedSpan(
            name = name,
            startTimeNanos = startTimeNanos,
            endTimeNanos = endTimeNanos,
            errorCode = null,
            parent = parent,
            attributes = null,
            events = null
        )

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?
    ): Boolean = recordCompletedSpan(
        name = name,
        startTimeNanos = startTimeNanos,
        endTimeNanos = endTimeNanos,
        errorCode = errorCode,
        parent = parent,
        attributes = null,
        events = null
    )

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?
    ): Boolean = recordCompletedSpan(
        name = name,
        startTimeNanos = startTimeNanos,
        endTimeNanos = endTimeNanos,
        errorCode = null,
        parent = null,
        attributes = attributes,
        events = events
    )

    override fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?
    ): Boolean =
        spansService.recordCompletedSpan(
            name = name,
            startTimeNanos = startTimeNanos,
            endTimeNanos = endTimeNanos,
            parent = parent,
            internal = false,
            attributes = attributes ?: emptyMap(),
            events = events ?: emptyList(),
            errorCode = errorCode
        )

    override fun getSpan(spanId: String): EmbraceSpan? = spansService.getSpan(spanId = spanId)

    override fun getSdkClockTimeNanos(): Long = clock.nowInNanos()

    @Deprecated("Not required. Use Embrace.isStarted() to know when the full tracing API is available")
    override fun isTracingAvailable(): Boolean = spansService.initialized()
}
