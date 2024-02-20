package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.Initializable
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

/**
 * Internal service that supports the creation and recording of [EmbraceSpan]
 */
internal interface SpanService : Initializable {
    /**
     * Return an [EmbraceSpan] instance that can be used to record spans. Returns null if at least one input parameter is not valid or if
     * the SDK or session is not in a state where a new span can be recorded.
     */
    fun createSpan(
        name: String,
        parent: EmbraceSpan? = null,
        type: EmbraceAttributes.Type = EmbraceAttributes.Type.PERFORMANCE,
        internal: Boolean = true
    ): EmbraceSpan?

    /**
     * Records a span around the execution of the given lambda. If the lambda throws an uncaught exception, it will be recorded as a
     * [ErrorCode.FAILURE]. The span will be the provided name, and the appropriate prefix will be prepended to it if [internal] is true.
     */
    fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan? = null,
        type: EmbraceAttributes.Type = EmbraceAttributes.Type.PERFORMANCE,
        internal: Boolean = true,
        attributes: Map<String, String> = emptyMap(),
        events: List<EmbraceSpanEvent> = emptyList(),
        code: () -> T
    ): T

    /**
     * Record a completed span for an operation with the given start and end times. Returns true if the span was recorded or queued to be
     * recorded, false if it wasn't.
     */
    fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        parent: EmbraceSpan? = null,
        type: EmbraceAttributes.Type = EmbraceAttributes.Type.PERFORMANCE,
        internal: Boolean = true,
        attributes: Map<String, String> = emptyMap(),
        events: List<EmbraceSpanEvent> = emptyList(),
        errorCode: ErrorCode? = null
    ): Boolean

    /**
     * Return the [EmbraceSpan] corresponding to the given spanId if it is active or it has completed in the current session
     */
    fun getSpan(spanId: String): EmbraceSpan?
}
