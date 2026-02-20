package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of the core logic for [SpanService]
 */
@OptIn(ExperimentalApi::class)
class SpanServiceImpl(
    private val tracer: Tracer,
    private val spanRepository: SpanRepository,
    private val embraceSpanFactory: EmbraceSpanFactory,
    private val dataValidator: DataValidator,
    private val canStartNewSpan: (parentSpan: EmbraceSpan?, internal: Boolean) -> Boolean,
    private val initCallback: (initTimeMs: Long) -> Unit,
    private val openTelemetry: OpenTelemetry,
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
    ): EmbraceSdkSpan {
        EmbTrace.trace("span-create") {
            return if (name.isNotBlank() && canStartNewSpan(parent, internal)) {
                embraceSpanFactory.create(
                    OtelSpanStartArgs(
                        name = dataValidator.truncateName(name, internal),
                        type = type,
                        internal = internal,
                        private = private,
                        tracer = tracer,
                        autoTerminationMode = autoTerminationMode,
                        parentCtx = (parent as? EmbraceSdkSpan)?.createContext(openTelemetry),
                        openTelemetry = openTelemetry,
                    )
                )
            } else {
                NoopEmbraceSdkSpan
            }
        }
    }

    override fun createSpan(otelSpanStartArgs: OtelSpanStartArgs): EmbraceSdkSpan {
        EmbTrace.trace("span-create") {
            return if (
                otelSpanStartArgs.initialSpanName.isNotBlank() &&
                canStartNewSpan(
                    otelSpanStartArgs.parentContext.getEmbraceSpan(openTelemetry),
                    otelSpanStartArgs.internal
                )
            ) {
                embraceSpanFactory.create(otelSpanStartArgs)
            } else {
                NoopEmbraceSdkSpan
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

            val validName = dataValidator.truncateName(name, internal)
            val validEvents = dataValidator.truncateEvents(events, internal)
            val validAttributes = dataValidator.truncateAttributes(attributes, internal)

            if (canStartNewSpan(parent, internal)) {
                val newSpan = embraceSpanFactory.create(
                    OtelSpanStartArgs(
                        name = validName,
                        type = type,
                        internal = internal,
                        private = private,
                        tracer = tracer,
                        parentCtx = (parent as? EmbraceSdkSpan)?.createContext(openTelemetry),
                        openTelemetry = openTelemetry,
                    )
                )
                if (newSpan.start(startTimeMs)) {
                    validAttributes.forEach {
                        newSpan.addAttribute(it.key, it.value)
                    }
                    validEvents.forEach {
                        newSpan.addEvent(it.name, it.timestampNanos.nanosToMillis(), it.attributes)
                    }
                    return newSpan.stop(errorCode, endTimeMs)
                }
            }

            return false
        }
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spanRepository.getSpan(spanId = spanId)
}
