package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.telemetry.TelemetryService
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [SpansService] that can be instantiated quickly. At that time, it will defer calls to the [SpansService] interface to a stubby
 * implementation that does nothing, to be continually used if the spans feature is not turned in. If after instantiate, we know that
 * the feature is on, you can change its delegation to an actual functioning [SpansService] implementation (i.e. [SpansServiceImpl])
 * by calling the [initializeService] method. It is recommended that this is done in the background rather than on the main thread because
 * it may not be fast and doing it in the background doesn't affect how it works.
 */
internal class EmbraceSpansService(
    private val clock: Clock,
    private val telemetryService: TelemetryService
) : Initializable, SpansService {
    /**
     * When this instance has been initialized with an instance of [SpansService] that does the proper spans logging
     */
    private val initialized = AtomicBoolean(false)

    // Not putting a limit on the number of calls to buffer because this is only being used internally
    // Once this is exposed to customers directly, we will restrict the calls to accept to something reasonable
    private val bufferedCalls = ConcurrentLinkedQueue<BufferedRecordCompletedSpan>()

    @Volatile
    private var sdkInitStartTime: Long? = null

    @Volatile
    private var currentDelegate: SpansService = SpansService.featureDisabledSpansService

    override fun initializeService(sdkInitStartTimeNanos: Long) {
        if (!initialized.get()) {
            sdkInitStartTime = sdkInitStartTimeNanos
            synchronized(initialized) {
                if (!initialized.get()) {
                    currentDelegate = SpansServiceImpl(
                        sdkInitStartTimeNanos = sdkInitStartTimeNanos,
                        clock = clock,
                        telemetryService = telemetryService
                    )
                    initialized.set(true)
                    recordBufferedCalls()
                }
            }
        }
    }

    override fun initialized(): Boolean = initialized.get()

    override fun createSpan(name: String, parent: EmbraceSpan?, type: EmbraceAttributes.Type, internal: Boolean): EmbraceSpan? =
        currentDelegate.createSpan(name = name, parent = parent, type = type, internal = internal)

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        code: () -> T
    ): T =
        currentDelegate.recordSpan(name = name, parent = parent, type = type, internal = internal, code = code)

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
        return if (!initialized()) {
            /**
             * Note: there's no way to public way to create an [EmbraceSpan] before the service is initialized, so while we buffer
             * the passed in [parent], we should never get a valid non-null value for it here.
             */
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
            recordBufferedCalls()
            true
        } else {
            currentDelegate.recordCompletedSpan(
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
        }
    }

    override fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode =
        currentDelegate.storeCompletedSpans(spans = spans)

    override fun completedSpans(): List<EmbraceSpanData>? = currentDelegate.completedSpans()

    override fun flushSpans(appTerminationCause: EmbraceAttributes.AppTerminationCause?): List<EmbraceSpanData>? =
        currentDelegate.flushSpans(appTerminationCause = appTerminationCause)

    override fun getSpan(spanId: String): EmbraceSpan? = currentDelegate.getSpan(spanId)

    private fun recordBufferedCalls() {
        if (initialized()) {
            synchronized(bufferedCalls) {
                do {
                    bufferedCalls.poll()?.let {
                        currentDelegate.recordCompletedSpan(
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
    }
}

/**
 * Represents a call to [EmbraceSpansService.recordCompletedSpan] that is saved to be invoked later when the service is initialized
 */
private data class BufferedRecordCompletedSpan(
    val name: String,
    val startTimeNanos: Long,
    val endTimeNanos: Long,
    val parent: EmbraceSpan?,
    val type: EmbraceAttributes.Type,
    val internal: Boolean,
    val attributes: Map<String, String>,
    val events: List<EmbraceSpanEvent>,
    val errorCode: ErrorCode?
)
