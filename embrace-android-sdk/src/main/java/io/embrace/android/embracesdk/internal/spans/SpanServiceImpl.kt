package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of the core logic for [SpanService]
 */
internal class SpanServiceImpl(
    private val spanRepository: SpanRepository,
    private val embraceSpanFactory: EmbraceSpanFactory,
    private val currentSessionSpan: CurrentSessionSpan,
) : SpanService {
    private val initialized = AtomicBoolean(false)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        synchronized(initialized) {
            currentSessionSpan.initializeService(sdkInitStartTimeMs)
            initialized.set(true)
        }
    }

    override fun initialized(): Boolean = initialized.get()

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean
    ): PersistableEmbraceSpan? {
        return if (inputsValid(name) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            embraceSpanFactory.create(
                name = name,
                type = type,
                internal = internal,
                private = private,
                parent = parent
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
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        code: () -> T
    ): T {
        val returnValue: T
        val span = createSpan(name = name, parent = parent, type = type, internal = internal, private = private)
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
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?
    ): Boolean {
        if (startTimeMs > endTimeMs) {
            return false
        }

        if (inputsValid(name, events, attributes) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            val newSpan = embraceSpanFactory.create(name = name, type = type, internal = internal, private = private, parent = parent)
            if (newSpan.start(startTimeMs)) {
                attributes.forEach {
                    newSpan.addAttribute(it.key, it.value)
                }
                events.forEach {
                    newSpan.addEvent(it.name, it.timestampNanos.nanosToMillis(), it.attributes)
                }
                return newSpan.stop(errorCode, endTimeMs)
            }
        }

        return false
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spanRepository.getSpan(spanId = spanId)

    private fun inputsValid(
        name: String,
        events: List<EmbraceSpanEvent>? = null,
        attributes: Map<String, String>? = null
    ) = name.isNotBlank() &&
        name.length <= EmbraceSpanImpl.MAX_NAME_LENGTH &&
        (events == null || events.size <= EmbraceSpanImpl.MAX_EVENT_COUNT) &&
        (attributes == null || attributes.size <= EmbraceSpanImpl.MAX_ATTRIBUTE_COUNT)

    companion object {
        const val MAX_NON_INTERNAL_SPANS_PER_SESSION = 500
    }
}
