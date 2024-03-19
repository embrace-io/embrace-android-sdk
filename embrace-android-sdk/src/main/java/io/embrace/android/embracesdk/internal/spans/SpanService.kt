package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.Initializable
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan

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
        type: TelemetryType = EmbType.Performance.Default,
        internal: Boolean = true
    ): PersistableEmbraceSpan?

    /**
     * Create, start, and return a new [EmbraceSpan] with the given parameters
     */
    fun startSpan(
        name: String,
        parent: EmbraceSpan? = null,
        startTimeMs: Long? = null,
        type: TelemetryType = EmbType.Performance.Default,
        internal: Boolean = true
    ): PersistableEmbraceSpan? {
        createSpan(
            name = name,
            parent = parent,
            type = type,
            internal = internal
        )?.let { newSpan ->
            if (newSpan.start(startTimeMs)) {
                return newSpan
            }
        }

        return null
    }

    /**
     * Records a span around the execution of the given lambda. If the lambda throws an uncaught exception, it will be recorded as a
     * [ErrorCode.FAILURE]. The span will be the provided name, and the appropriate prefix will be prepended to it if [internal] is true.
     */
    fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan? = null,
        type: TelemetryType = EmbType.Performance.Default,
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
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan? = null,
        type: TelemetryType = EmbType.Performance.Default,
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
