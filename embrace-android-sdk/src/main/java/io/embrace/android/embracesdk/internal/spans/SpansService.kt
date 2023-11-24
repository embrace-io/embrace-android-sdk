package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Public interface for an internal service that manages the recording, storage, and propagation of Spans
 */
internal interface SpansService {
    /**
     * Return an [EmbraceSpan] that can be started and stopped
     */
    fun createSpan(
        name: String,
        parent: EmbraceSpan? = null,
        type: EmbraceAttributes.Type = EmbraceAttributes.Type.PERFORMANCE,
        internal: Boolean = true
    ): EmbraceSpan?

    /**
     * Record a key span around the given lambda with the current session span as its parent where the start time will be when the lambda
     * starts and the end time will be when the lambda ends. If the lambda throws an exception, it will be recorded as a
     * [ErrorCode.FAILURE]. The name of the span will be the provided name with the appropriate prefix prepended to it
     * if [internal] is true.
     */
    fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan? = null,
        type: EmbraceAttributes.Type = EmbraceAttributes.Type.PERFORMANCE,
        internal: Boolean = true,
        recordSystrace: Boolean = false,
        code: () -> T
    ): T

    /**
     * Record a completed [Span] for work that has already been done. Returns true if the span was recorded or queued to be recorded,
     * false if it wasn't.
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
     * Store the given list of completed Spans to be sent to the backend at the next available time
     */
    fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode

    /**
     * Return the list of the currently stored completed Spans that have not been sent to the backend
     */
    fun completedSpans(): List<EmbraceSpanData>?

    /**
     * Flush and return all of the stored completed Spans. This should be called when the stored completed spans are ready to be sent to
     * the backend. If the flush is being triggered because the app is about to terminate, set [appTerminationCause] to the appropriate
     * value. Setting this to null means the app is not terminating.
     */
    fun flushSpans(appTerminationCause: EmbraceAttributes.AppTerminationCause? = null): List<EmbraceSpanData>?

    companion object {
        val featureDisabledSpansService = FeatureDisabledSpansService()
        val systraceOnlySpansService = FeatureDisabledSpansService(true)
    }
}
