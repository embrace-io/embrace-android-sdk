package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.utils.EmbTrace
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
    private val dataValidator: DataValidator,
    private val canStartNewSpan: (parentSpan: EmbraceSpan?, internal: Boolean) -> Boolean,
    private val initCallback: (initTimeMs: Long) -> Unit
) : SpanService {
    private val initialized = AtomicBoolean(false)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        synchronized(initialized) {
            initCallback(sdkInitStartTimeMs)
            initialized.set(true)
        }
    }

    override fun initialized(): Boolean = initialized.get()

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSdkSpan? {
        EmbTrace.trace("span-create") {
            return if (dataValidator.isNameValid(name, internal) && canStartNewSpan(parent, internal)) {
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
    }

    override fun createSpan(otelSpanBuilderWrapper: OtelSpanBuilderWrapper): EmbraceSdkSpan? {
        EmbTrace.trace("span-create") {
            return if (
                dataValidator.isNameValid(otelSpanBuilderWrapper.initialSpanName, otelSpanBuilderWrapper.internal) &&
                canStartNewSpan(
                    otelSpanBuilderWrapper.getParentContext().getEmbraceSpan(),
                    otelSpanBuilderWrapper.internal
                )
            ) {
                embraceSpanFactory.create(otelSpanBuilderWrapper)
            } else {
                null
            }
        }
    }

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
    ): T {
        val returnValue: T
        val span = createSpan(
            name = name,
            parent = parent,
            type = type,
            internal = internal,
            private = private,
            autoTerminationMode = autoTerminationMode
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
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ): Boolean {
        EmbTrace.trace("span-completed") {
            if (startTimeMs > endTimeMs) {
                return false
            }

            if (inputsValid(name, internal, events, attributes) && canStartNewSpan(parent, internal)) {
                val newSpan = embraceSpanFactory.create(
                    name = name,
                    type = type,
                    internal = internal,
                    private = private,
                    parent = parent,
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
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spanRepository.getSpan(spanId = spanId)

    private fun inputsValid(
        name: String,
        internal: Boolean,
        events: List<EmbraceSpanEvent>,
        attributes: Map<String, String>,
    ): Boolean = dataValidator.isNameValid(name, internal) &&
        dataValidator.isEventCountValid(events, internal) &&
        dataValidator.isAttributeCountValid(attributes, internal)
}
