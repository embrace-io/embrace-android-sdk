package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of the core logic for [SpansService]
 */
internal class SpansServiceImpl(
    private val spansRepository: SpansRepository,
    private val currentSessionSpan: CurrentSessionSpan,
    private val tracer: Tracer,
) : SpansService {
    private val initialized = AtomicBoolean(false)

    override fun initializeService(sdkInitStartTimeNanos: Long) {
        synchronized(initialized) {
            currentSessionSpan.initializeService(sdkInitStartTimeNanos)
            initialized.set(true)
        }
    }

    override fun initialized(): Boolean = initialized.get()

    override fun createSpan(name: String, parent: EmbraceSpan?, type: EmbraceAttributes.Type, internal: Boolean): EmbraceSpan? {
        return if (EmbraceSpanImpl.inputsValid(name) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            EmbraceSpanImpl(
                spanBuilder = createRootSpanBuilder(tracer = tracer, name = name, type = type, internal = internal),
                parent = parent,
                spansRepository = spansRepository
            )
        } else {
            null
        }
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
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
        startTimeNanos: Long,
        endTimeNanos: Long,
        parent: EmbraceSpan?,
        type: EmbraceAttributes.Type,
        internal: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?
    ): Boolean {
        if (startTimeNanos > endTimeNanos) {
            return false
        }

        return if (EmbraceSpanImpl.inputsValid(name, events, attributes) &&
            currentSessionSpan.canStartNewSpan(parent, internal)
        ) {
            createRootSpanBuilder(tracer = tracer, name = name, type = type, internal = internal)
                .updateParent(parent)
                .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
                .startSpan()
                .setAllAttributes(Attributes.builder().fromMap(attributes).build())
                .addEvents(events)
                .endSpan(errorCode, endTimeNanos)
            true
        } else {
            false
        }
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spansRepository.getSpan(spanId = spanId)

    companion object {
        const val MAX_NON_INTERNAL_SPANS_PER_SESSION = 500
    }
}
