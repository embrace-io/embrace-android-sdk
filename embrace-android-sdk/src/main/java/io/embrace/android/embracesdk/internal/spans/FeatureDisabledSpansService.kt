package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * An implementation of [SpansService] that does nothing, to be used when the feature is not enabled
 */
@InternalApi
internal class FeatureDisabledSpansService : SpansService {
    override fun createSpan(name: String, parent: EmbraceSpan?, type: EmbraceAttributes.Type, internal: Boolean): EmbraceSpan? = null

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        code: () -> T
    ) = code()

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
    ): Boolean = false

    override fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode = CompletableResultCode.ofFailure()

    override fun completedSpans(): List<EmbraceSpanData>? = null

    override fun flushSpans(appTerminationCause: EmbraceAttributes.AppTerminationCause?): List<EmbraceSpanData>? = null

    override fun getSpan(spanId: String): EmbraceSpan? = null
}
