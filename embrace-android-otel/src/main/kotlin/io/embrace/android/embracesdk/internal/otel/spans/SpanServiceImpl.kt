package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer

/**
 * Implementation of [SpanService].
 */
class SpanServiceImpl(
    private val spanRepository: SpanRepository,
    private val dataValidator: DataValidator,
    private val canStartNewSpan: (parentSpan: EmbraceSpan?, internal: Boolean, type: EmbType) -> Boolean,
    private val embraceSpanFactory: EmbraceSpanFactory,
    private val tracer: Tracer,
    private val openTelemetry: OpenTelemetry,
) : SpanService {

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        terminationMode: SpanTerminationMode,
    ): EmbraceSdkSpan {
        return if (name.isNotBlank() && canStartNewSpan(parent, internal, type)) {
            embraceSpanFactory.create(
                OtelSpanStartArgs(
                    name = dataValidator.truncateName(name, internal),
                    type = type,
                    internal = internal,
                    private = private,
                    tracer = tracer,
                    terminationMode = terminationMode,
                    parentCtx = (parent as? EmbraceSdkSpan)?.createContext(openTelemetry),
                    openTelemetry = openTelemetry,
                ),
            )
        } else {
            NoopEmbraceSdkSpan
        }
    }

    override fun createSpan(otelSpanStartArgs: OtelSpanStartArgs): EmbraceSdkSpan {
        return if (
            otelSpanStartArgs.initialSpanName.isNotBlank() &&
            canStartNewSpan(
                otelSpanStartArgs.parentContext.getEmbraceSpan(openTelemetry),
                otelSpanStartArgs.internal,
                otelSpanStartArgs.type,
            )
        ) {
            embraceSpanFactory.create(otelSpanStartArgs)
        } else {
            NoopEmbraceSdkSpan
        }
    }

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        terminationMode: SpanTerminationMode,
    ): EmbraceSdkSpan {
        val newSpan = createSpan(
            name = name,
            parent = parent,
            type = type,
            internal = internal,
            private = private,
            terminationMode = terminationMode,
        )
        return when {
            newSpan.start(startTimeMs) -> newSpan
            else -> NoopEmbraceSdkSpan
        }
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, Any>,
        events: List<SpanEvent>,
        terminationMode: SpanTerminationMode,
        code: () -> T,
    ): T {
        val returnValue: T
        val span = createSpan(
            name = name,
            parent = parent,
            type = type,
            internal = internal,
            private = private,
            terminationMode = terminationMode,
        )
        try {
            if (span.start()) {
                attributes.forEach { attribute ->
                    span.addCustomAttribute(attribute.key, attribute.value)
                }
                events.forEach { event ->
                    span.addCustomEvent(
                        event.name,
                        event.timestampNanos.nanosToMillis(),
                        event.attributes,
                    )
                }
            }
            returnValue = code()
            span.stop()
        } catch (t: Throwable) {
            span.stopWithErrorCode(ErrorCodeAttribute.Failure)
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
        attributes: Map<String, Any>,
        events: List<SpanEvent>,
        errorCode: ErrorCodeAttribute?,
    ): Boolean {
        // callers may supply nanosecond timestamps - normalize before validating so that the check below compares like with like
        val validStartTimeMs = startTimeMs.normalizeTimestampAsMillis()
        val validEndTimeMs = endTimeMs.normalizeTimestampAsMillis()

        if (validStartTimeMs > validEndTimeMs) {
            return false
        }

        val validName = dataValidator.truncateName(name, internal)
        val validEvents = dataValidator.truncateEvents(events, internal)
        val validAttributes = dataValidator.truncateAttributes(attributes, internal)

        if (canStartNewSpan(parent, internal, type)) {
            val newSpan = embraceSpanFactory.create(
                OtelSpanStartArgs(
                    name = validName,
                    type = type,
                    internal = internal,
                    private = private,
                    tracer = tracer,
                    parentCtx = (parent as? EmbraceSdkSpan)?.createContext(openTelemetry),
                    openTelemetry = openTelemetry,
                ),
            )
            if (newSpan.start(validStartTimeMs)) {
                validAttributes.forEach {
                    newSpan.addCustomAttribute(it.key, it.value)
                }
                validEvents.forEach {
                    newSpan.addCustomEvent(it.name, it.timestampNanos.nanosToMillis(), it.attributes)
                }
                return newSpan.stopWithErrorCode(errorCode, endTimeMs)
            }
        }

        return false
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spanRepository.getEmbraceSpan(spanId = spanId)
}
