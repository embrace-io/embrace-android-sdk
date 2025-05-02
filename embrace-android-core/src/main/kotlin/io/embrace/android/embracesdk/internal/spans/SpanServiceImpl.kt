package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.isAttributeCountValid
import io.embrace.android.embracesdk.internal.config.instrumented.isEventCountValid
import io.embrace.android.embracesdk.internal.config.instrumented.isNameValid
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of the core logic for [SpanService]
 */
internal class SpanServiceImpl(
    private val spanRepository: SpanRepository,
    private val embraceSpanFactory: EmbraceSpanFactory,
    private val currentSessionSpan: CurrentSessionSpan,
    private val limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
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
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
    ): PersistableEmbraceSpan? {
        return if (limits.isNameValid(name, internal) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            embraceSpanFactory.create(
                name = name,
                type = type,
                internal = internal,
                private = private,
                parent = parent,
                autoTerminationMode = autoTerminationMode,
            )
        } else {
            null
        }
    }

    override fun createSpan(embraceSpanBuilder: EmbraceSpanBuilder): PersistableEmbraceSpan? {
        return if (
            limits.isNameValid(embraceSpanBuilder.spanName, embraceSpanBuilder.internal) &&
            currentSessionSpan.canStartNewSpan(embraceSpanBuilder.getParentSpan(), embraceSpanBuilder.internal)
        ) {
            embraceSpanFactory.create(embraceSpanBuilder)
        } else {
            null
        }
    }

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
    ): T {
        val returnValue: T
        val span = createSpan(
            name = name,
            autoTerminationMode = autoTerminationMode,
            parent = parent,
            type = type,
            internal = internal,
            private = private
        )
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
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ): Boolean {
        if (startTimeMs > endTimeMs) {
            return false
        }

        if (inputsValid(name, internal, events, attributes) && currentSessionSpan.canStartNewSpan(parent, internal)) {
            val newSpan = embraceSpanFactory.create(
                name = name,
                type = type,
                internal = internal,
                private = private,
                parent = parent,
                autoTerminationMode = autoTerminationMode,
            )
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
        internal: Boolean,
        events: List<EmbraceSpanEvent>,
        attributes: Map<String, String>,
    ): Boolean = limits.isNameValid(name, internal) &&
        limits.isEventCountValid(events, internal) &&
        limits.isAttributeCountValid(attributes, internal)

    companion object {
        const val MAX_INTERNAL_SPANS_PER_SESSION: Int = 5000
        const val MAX_NON_INTERNAL_SPANS_PER_SESSION: Int = 500
    }
}
