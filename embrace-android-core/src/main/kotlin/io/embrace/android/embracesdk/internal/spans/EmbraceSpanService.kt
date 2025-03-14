package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

/**
 * A [SpanService] that can be instantiated quickly. At that time, it will defer calls an implementation that handles the case when
 * the SDK has not been started ([UninitializedSdkSpanService]. When [initializeService] is called during SDK startup, it will
 * instantiate and initialize [SpanServiceImpl] to provide the span recording functionality.
 */
internal class EmbraceSpanService(
    private val spanRepository: SpanRepository,
    private val currentSessionSpan: CurrentSessionSpan,
    private val embraceSpanFactorySupplier: Provider<EmbraceSpanFactory>,
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
                        embraceSpanFactory = embraceSpanFactorySupplier(),
                        currentSessionSpan = currentSessionSpan,
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

    override fun createSpan(
        name: String,
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
    ): PersistableEmbraceSpan? =
        currentDelegate.createSpan(
            name = name,
            autoTerminationMode = autoTerminationMode,
            parent = parent,
            type = type,
            internal = internal,
            private = private
        )

    override fun createSpan(embraceSpanBuilder: EmbraceSpanBuilder): PersistableEmbraceSpan? =
        currentDelegate.createSpan(
            embraceSpanBuilder
        )

    override fun <T> recordSpan(
        name: String,
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        code: () -> T,
    ): T = currentDelegate.recordSpan(
        name = name,
        autoTerminationMode = autoTerminationMode,
        parent = parent,
        type = type,
        internal = internal,
        private = private,
        attributes = attributes,
        events = events,
        code = code
    )

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ): Boolean = currentDelegate.recordCompletedSpan(
        name = name,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        autoTerminationMode = autoTerminationMode,
        parent = parent,
        type = type,
        internal = internal,
        private = private,
        attributes = attributes,
        events = events,
        errorCode = errorCode
    )

    override fun getSpan(spanId: String): EmbraceSpan? = currentDelegate.getSpan(spanId = spanId)
}
