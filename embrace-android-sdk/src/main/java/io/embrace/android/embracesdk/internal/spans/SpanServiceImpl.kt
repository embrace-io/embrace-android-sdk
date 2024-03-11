package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of the core logic for [SpanService]
 */
internal class SpanServiceImpl(
    private val spanRepository: SpanRepository,
    private val currentSessionSpan: CurrentSessionSpan,
    private val tracer: Tracer,
) : SpanService {
    private val initialized = AtomicBoolean(false)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        synchronized(initialized) {
            currentSessionSpan.initializeService(sdkInitStartTimeMs)
            initialized.set(true)
        }
    }

    override fun initialized(): Boolean = initialized.get()

    override fun createSpan(name: String, parent: EmbraceSpan?, type: TelemetryType, internal: Boolean): EmbraceSpan? {
        return if (EmbraceSpanImpl.inputsValid(name) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            EmbraceSpanImpl(
                spanBuilder = createRootSpanBuilder(tracer = tracer, name = name, type = type, internal = internal),
                parent = parent,
                spanRepository = spanRepository
            )
        } else {
            null
        }
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        code: () -> T
    ): T {
        return if (EmbraceSpanImpl.inputsValid(name) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            createRootSpanBuilder(tracer = tracer, name = name, type = type, internal = internal)
                .updateParent(parent)
                .record(attributes, events, code)
        } else {
            code()
        }
    }

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
    ): Boolean {
        if (startTimeMs > endTimeMs) {
            return false
        }

        return if (EmbraceSpanImpl.inputsValid(name, events, attributes) &&
            currentSessionSpan.canStartNewSpan(parent, internal)
        ) {
            createRootSpanBuilder(tracer = tracer, name = name, type = type, internal = internal)
                .updateParent(parent)
                .setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS)
                .startSpan()
                .setAllAttributes(Attributes.builder().fromMap(attributes).build())
                .addEvents(events)
                .endSpan(errorCode, endTimeMs)
            true
        } else {
            false
        }
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spanRepository.getSpan(spanId = spanId)

    companion object {
        const val MAX_NON_INTERNAL_SPANS_PER_SESSION = 500
    }
}
