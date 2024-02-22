package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * An implementation of [SpanService] used when the SDK has not been started.
 */
@InternalApi
internal class UninitializedSdkSpanService : SpanService {
    private val bufferedCalls = ConcurrentLinkedQueue<BufferedRecordCompletedSpan>()
    private val bufferedCallsCount = AtomicInteger(0)
    private val realSpanService: AtomicReference<SpanService?> = AtomicReference(null)

    override fun initializeService(sdkInitStartTimeMs: Long) {}

    override fun initialized(): Boolean = true

    override fun createSpan(name: String, parent: EmbraceSpan?, type: EmbraceAttributes.Type, internal: Boolean): EmbraceSpan? = null

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        code: () -> T
    ) = code()

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?
    ): Boolean {
        return realSpanService.get()?.recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            parent = parent,
            type = type,
            internal = internal,
            attributes = attributes,
            events = events,
            errorCode = errorCode
        ) ?: if (bufferedCallsCount.getAndIncrement() < MAX_BUFFERED_CALLS) {
            // Note: there's no public way to create an [EmbraceSpan] before the service is initialized, so while we buffer
            // the passed in [parent], we should never get a valid non-null value for it here.
            synchronized(bufferedCalls) {
                bufferedCalls.add(
                    BufferedRecordCompletedSpan(
                        name = name,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        parent = parent,
                        type = type,
                        internal = internal,
                        attributes = attributes,
                        events = events,
                        errorCode = errorCode
                    )
                )
            }
            true
        } else {
            false
        }
    }

    override fun getSpan(spanId: String): EmbraceSpan? = null

    /**
     * Set the real [SpanService] to record completed spans and record the buffered instances
     */
    fun triggerBufferedSpanRecording(delegateSpanService: SpanService) {
        synchronized(bufferedCalls) {
            realSpanService.set(delegateSpanService)
            do {
                bufferedCalls.poll()?.let {
                    delegateSpanService.recordCompletedSpan(
                        name = it.name,
                        startTimeMs = it.startTimeMs,
                        endTimeMs = it.endTimeMs,
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
     * Represents a call to [SpanService.recordCompletedSpan] that can be saved and replayed later when the SDK is initialized.
     */
    data class BufferedRecordCompletedSpan(
        val name: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val parent: EmbraceSpan?,
        val type: EmbraceAttributes.Type,
        val internal: Boolean,
        val attributes: Map<String, String>,
        val events: List<EmbraceSpanEvent>,
        val errorCode: ErrorCode?,
    )
}
