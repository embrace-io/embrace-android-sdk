package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.Tracer

/**
 * A [SpansService] that can be instantiated quickly. At that time, it will defer calls an implementation that handles the case when
 * the SDK has not been started ([UninitializedSdkSpansService]. When [initializeService] is called during SDK startup, it will
 * instantiate and initialize [SpansServiceImpl] to provide the span recording functionality.
 */
internal class EmbraceSpansService(
    private val spansRepository: SpansRepository,
    private val currentSessionSpan: CurrentSessionSpan,
    private val tracerSupplier: Provider<Tracer>,
) : SpansService {
    private val uninitializedSdkSpansService: UninitializedSdkSpansService = UninitializedSdkSpansService()

    @Volatile
    private var currentDelegate: SpansService = uninitializedSdkSpansService

    override fun initializeService(sdkInitStartTimeNanos: Long) {
        if (!initialized()) {
            synchronized(currentDelegate) {
                if (!initialized()) {
                    currentDelegate = SpansServiceImpl(
                        spansRepository = spansRepository,
                        currentSessionSpan = currentSessionSpan,
                        tracer = tracerSupplier(),
                    )
                    currentDelegate.initializeService(sdkInitStartTimeNanos)
                    if (currentDelegate.initialized()) {
                        uninitializedSdkSpansService.recordBufferedCalls(this)
                    }
                }
            }
        }
    }

    override fun initialized(): Boolean = currentDelegate is SpansServiceImpl

    override fun createSpan(name: String, parent: EmbraceSpan?, type: EmbraceAttributes.Type, internal: Boolean): EmbraceSpan? =
        currentDelegate.createSpan(name = name, parent = parent, type = type, internal = internal)

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        code: () -> T
    ): T = currentDelegate.recordSpan(name = name, parent = parent, type = type, internal = internal, code = code)

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

    override fun getSpan(spanId: String): EmbraceSpan? = currentDelegate.getSpan(spanId = spanId)
}
