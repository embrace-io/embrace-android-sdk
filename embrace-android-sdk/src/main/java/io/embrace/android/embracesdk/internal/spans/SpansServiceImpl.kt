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
    private val spansSink: SpansSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val tracer: Tracer,
) : SpansService, SpansSink by spansSink {
    private val initialized = AtomicBoolean(false)

    override fun initializeService(sdkInitStartTimeNanos: Long) {
        synchronized(initialized) {
            currentSessionSpan.startInitialSession(sdkInitStartTimeNanos)
            initialized.set(true)
        }
    }

    override fun initialized(): Boolean = initialized.get()

    override fun createSpan(name: String, parent: EmbraceSpan?, type: EmbraceAttributes.Type, internal: Boolean): EmbraceSpan? {
        return if (EmbraceSpanImpl.inputsValid(name) && currentSessionSpan.validateAndUpdateContext(parent, internal)) {
            EmbraceSpanImpl(
                spanBuilder = createRootSpanBuilder(tracer = tracer, name = name, type = type, internal = internal),
                parent = parent,
                spansRepository = spansSink.getSpansRepository()
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
        code: () -> T
    ): T {
        return if (EmbraceSpanImpl.inputsValid(name) && currentSessionSpan.validateAndUpdateContext(parent, internal)) {
            createRootSpanBuilder(tracer = tracer, name = name, type = type, internal = internal).updateParent(parent).record(code)
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
            currentSessionSpan.validateAndUpdateContext(parent, internal)
        ) {
            val span = createRootSpanBuilder(tracer = tracer, name = name, type = type, internal = internal)
                .updateParent(parent)
                .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
                .startSpan()
                .setAllAttributes(Attributes.builder().fromMap(attributes).build())

            events.forEach { event ->
                if (EmbraceSpanEvent.inputsValid(event.name, event.attributes)) {
                    span.addEvent(
                        event.name,
                        Attributes.builder().fromMap(event.attributes).build(),
                        event.timestampNanos,
                        TimeUnit.NANOSECONDS
                    )
                }
            }

            span.endSpan(errorCode, endTimeNanos)
            true
        } else {
            false
        }
    }

    companion object {
        const val MAX_TRACE_COUNT_PER_SESSION = 100
        const val MAX_SPAN_COUNT_PER_TRACE = 10
    }
}
