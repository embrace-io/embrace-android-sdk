package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.Tracer

/**
 * A [SpanService] that can be instantiated quickly. At that time, it will defer calls an implementation that handles the case when
 * the SDK has not been started ([UninitializedSdkSpanService]. When [initializeService] is called during SDK startup, it will
 * instantiate and initialize [SpanServiceImpl] to provide the span recording functionality.
 */
internal class EmbraceSpanService(
    private val spanRepository: SpanRepository,
    private val currentSessionSpan: CurrentSessionSpan,
    private val tracerSupplier: Provider<Tracer>,
) : SpanService {
    private val uninitializedSdkSpansService: UninitializedSdkSpanService = UninitializedSdkSpanService()

    @Volatile
    private var currentDelegate: SpanService = uninitializedSdkSpansService

    override fun initializeService(sdkInitStartTimeMs: Long) {
        if (!initialized()) {
            synchronized(currentDelegate) {
                if (!initialized()) {
                    val realSpansService = SpanServiceImpl(
                        spanRepository = spanRepository,
                        currentSessionSpan = currentSessionSpan,
                        tracer = tracerSupplier(),
                    )
                    realSpansService.initializeService(sdkInitStartTimeMs)
                    if (realSpansService.initialized()) {
                        uninitializedSdkSpansService.triggerBufferedSpanRecording(realSpansService)
                    }
                    currentDelegate = realSpansService
                }
            }
        }
    }

    override fun initialized(): Boolean = currentDelegate is SpanServiceImpl

    override fun createSpan(name: String, parent: EmbraceSpan?, type: TelemetryType, internal: Boolean): EmbraceSpan? =
        currentDelegate.createSpan(name = name, parent = parent, type = type, internal = internal)

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        code: () -> T
    ): T = currentDelegate.recordSpan(
        name = name,
        parent = parent,
        type = type,
        internal = internal,
        attributes = attributes,
        events = events,
        code = code
    )

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?
    ): Boolean = currentDelegate.recordCompletedSpan(
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

    override fun getSpan(spanId: String): EmbraceSpan? = currentDelegate.getSpan(spanId = spanId)
}
