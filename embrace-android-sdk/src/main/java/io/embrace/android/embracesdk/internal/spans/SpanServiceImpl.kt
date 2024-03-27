package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of the core logic for [SpanService]
 */
internal class SpanServiceImpl(
    private val openTelemetryClock: Clock,
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

    override fun createSpan(name: String, parent: EmbraceSpan?, type: TelemetryType, internal: Boolean): PersistableEmbraceSpan? {
        return if (EmbraceSpanImpl.inputsValid(name) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            val spanName = getSpanName(name = name, internal = internal)
            EmbraceSpanImpl(
                spanName = spanName,
                openTelemetryClock = openTelemetryClock,
                spanBuilder = tracer.embraceSpanBuilder(name = spanName, type = type, internal = internal, parent = parent),
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
        val returnValue: T
        val span = createSpan(name = name, parent = parent, type = type, internal = internal)
        try {
            val started = span?.start() ?: false
            if (started) {
                attributes.forEach { attribute ->
                    span?.addAttribute(attribute.key, attribute.value)
                }
                events.forEach { event ->
                    span?.addEvent(
                        event.name,
                        event.timestampNanos.nanosToMillis(),
                        event.attributes
                    )
                }
            }
            returnValue = code()
            span?.stop()
        } catch (t: Throwable) {
            span?.stop(ErrorCode.FAILURE)
            throw t
        }

        return returnValue
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

        return if (EmbraceSpanImpl.inputsValid(name, events, attributes) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            tracer.embraceSpanBuilder(name = getSpanName(name, internal), type = type, internal = internal, parent = parent)
                .startSpan(startTimeMs)
                .setAllAttributes(Attributes.builder().fromMap(attributes).build())
                .addEvents(events)
                .endSpan(errorCode, endTimeMs)
            true
        } else {
            false
        }
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spanRepository.getSpan(spanId = spanId)

    private fun getSpanName(name: String, internal: Boolean): String =
        if (internal) {
            name.toEmbraceObjectName()
        } else {
            name
        }

    companion object {
        const val MAX_NON_INTERNAL_SPANS_PER_SESSION = 500
    }
}
