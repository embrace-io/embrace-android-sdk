package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
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
class EmbraceSpanService(
    private val spanRepository: SpanRepository,
    private val dataValidator: DataValidator,
    private val canStartNewSpan: (parentSpan: EmbraceSpan?, internal: Boolean) -> Boolean,
    private val initCallback: (initTimeMs: Long) -> Unit,
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
                        dataValidator = dataValidator,
                        embraceSpanFactory = embraceSpanFactorySupplier(),
                        canStartNewSpan = canStartNewSpan,
                        initCallback = initCallback
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
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSdkSpan? =
        currentDelegate.createSpan(
            name = name,
            parent = parent,
            type = type,
            internal = internal,
            private = private,
            autoTerminationMode = autoTerminationMode
        )

    override fun createSpan(otelSpanCreator: OtelSpanCreator): EmbraceSdkSpan? = currentDelegate.createSpan(otelSpanCreator)

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T = currentDelegate.recordSpan(
        name = name,
        parent = parent,
        type = type,
        internal = internal,
        private = private,
        attributes = attributes,
        events = events,
        autoTerminationMode = autoTerminationMode,
        code = code
    )

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ): Boolean = currentDelegate.recordCompletedSpan(
        name = name,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
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
