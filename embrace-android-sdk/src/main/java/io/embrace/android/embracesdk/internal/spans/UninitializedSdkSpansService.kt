package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * An implementation of [SpansService] used when the SDK is not enabled
 */
@InternalApi
internal class UninitializedSdkSpansService : SpansService {

    private val bufferedCalls = ConcurrentLinkedQueue<BufferedRecordCompletedSpan>()
    private val bufferedCallsCount = AtomicInteger(0)

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
    ): Boolean {
        return if (bufferedCallsCount.getAndIncrement() < MAX_BUFFERED_CALLS) {
            // Note: there's no public way to create an [EmbraceSpan] before the service is initialized, so while we buffer
            // the passed in [parent], we should never get a valid non-null value for it here.
            bufferedCalls.add(
                BufferedRecordCompletedSpan(
                    name = name,
                    startTimeNanos = startTimeNanos,
                    endTimeNanos = endTimeNanos,
                    parent = parent,
                    type = type,
                    internal = internal,
                    attributes = attributes,
                    events = events,
                    errorCode = errorCode
                )
            )
            true
        } else {
            false
        }
    }

    override fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode = CompletableResultCode.ofFailure()

    override fun completedSpans(): List<EmbraceSpanData> = emptyList()

    override fun flushSpans(): List<EmbraceSpanData> = emptyList()

    override fun getSpan(spanId: String): EmbraceSpan? = null

    override fun getSpansRepository(): SpansRepository? = null

    fun recordBufferedCalls(delegateSpansService: SpansService) {
        synchronized(bufferedCalls) {
            do {
                bufferedCalls.poll()?.let {
                    delegateSpansService.recordCompletedSpan(
                        name = it.name,
                        startTimeNanos = it.startTimeNanos,
                        endTimeNanos = it.endTimeNanos,
                        parent = it.parent,
                        type = it.type,
                        internal = it.internal,
                        attributes = it.attributes,
                        events = it.events,
                        errorCode = it.errorCode
                    )
                }
            } while (bufferedCalls.isNotEmpty())
        }
    }

    companion object {
        private const val MAX_BUFFERED_CALLS = 1000
    }

    /**
     * Represents a call to [EmbraceSpansService.recordCompletedSpan] that is saved to be invoked later when the service is initialized
     */
    data class BufferedRecordCompletedSpan(
        val name: String,
        val startTimeNanos: Long,
        val endTimeNanos: Long,
        val parent: EmbraceSpan?,
        val type: EmbraceAttributes.Type,
        val internal: Boolean,
        val attributes: Map<String, String>,
        val events: List<EmbraceSpanEvent>,
        val errorCode: ErrorCode?,
    )
}
