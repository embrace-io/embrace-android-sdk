package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [SpansService] that can be instantiated quickly. At that time, it will defer calls to the [SpansService] interface to a stubby
 * implementation that does nothing, to be continually used if the spans feature is not turned in. If after instantiate, we know that
 * the feature is on, you can change its delegation to an actual functioning [SpansService] implementation (i.e. [SpansServiceImpl])
 * by calling the [initializeService] method. It is recommended that this is done in the background rather than on the main thread because
 * it may not be fast and doing it in the background doesn't affect how it works.
 */
internal class EmbraceSpansService(
    private val spansSink: SpansSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val tracer: Tracer,
) : Initializable, SpansService {
    /**
     * When this instance has been initialized with an instance of [SpansService] that does the proper spans logging
     */
    private val initialized = AtomicBoolean(false)

    private val uninitializedSdkSpansService: UninitializedSdkSpansService = UninitializedSdkSpansService()

    @Volatile
    private var currentDelegate: SpansService = uninitializedSdkSpansService

    override fun initializeService(sdkInitStartTimeNanos: Long) {
        if (!initialized.get()) {
            synchronized(initialized) {
                if (!initialized.get()) {
                    currentSessionSpan.startInitialSession(sdkInitStartTimeNanos)
                    currentDelegate = SpansServiceImpl(
                        spansSink = spansSink,
                        currentSessionSpan = currentSessionSpan,
                        tracer = tracer,
                    )
                    initialized.set(true)
                    uninitializedSdkSpansService.recordBufferedCalls(this)
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
    ): Boolean = currentDelegate.recordCompletedSpan(
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

    override fun storeCompletedSpans(spans: List<SpanData>): CompletableResultCode =
        currentDelegate.storeCompletedSpans(spans = spans)

    override fun completedSpans(): List<EmbraceSpanData> = currentDelegate.completedSpans()

    override fun flushSpans(): List<EmbraceSpanData> = currentDelegate.flushSpans()

    override fun getSpan(spanId: String): EmbraceSpan? = currentDelegate.getSpan(spanId)

    override fun getSpansRepository(): SpansRepository? = currentDelegate.getSpansRepository()
}
