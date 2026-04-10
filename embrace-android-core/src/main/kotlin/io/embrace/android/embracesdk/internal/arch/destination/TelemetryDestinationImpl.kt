package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SessionPartStateToken
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.datasource.UnrecordedTransitions
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.embrace.android.embracesdk.internal.otel.sdk.setEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.logging.model.SeverityNumber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TelemetryDestinationImpl(
    private val clock: Clock,
    private val spanService: SpanService,
    private val eventService: EventService,
    private val currentSessionPartSpan: CurrentSessionPartSpan,
) : TelemetryDestination {

    override var sessionUpdateAction: (() -> Unit)? = null
    override var currentStatesProvider: () -> Map<String, Any> = { emptyMap() }

    override fun addLog(
        schemaType: SchemaType,
        severity: LogSeverity,
        message: String,
        isPrivate: Boolean,
        addCurrentSessionInfo: Boolean,
        timestampMs: Long?,
    ) {
        val logTimeMs = timestampMs ?: clock.now()
        val severityNumber = when (severity) {
            LogSeverity.INFO -> SeverityNumber.INFO
            LogSeverity.WARNING -> SeverityNumber.WARN
            LogSeverity.ERROR -> SeverityNumber.ERROR
        }
        val logTimeNanos = TimeUnit.MILLISECONDS.toNanos(logTimeMs)
        eventService.log(
            eventName = null,
            body = message,
            timestamp = logTimeNanos,
            observedTimestamp = logTimeNanos,
            context = null,
            severityNumber = severityNumber,
            severityText = getSeverityText(severityNumber),
            addCurrentMetadata = addCurrentSessionInfo
        ) {
            if (isPrivate) {
                setStringAttribute(PrivateSpan.key, PrivateSpan.value)
            }

            with(schemaType) {
                setStringAttribute(telemetryType.key, telemetryType.value)
                attributes().forEach {
                    setStringAttribute(it.key, it.value)
                }
            }
        }

        sessionUpdateAction?.invoke()
    }

    override fun startSpanCapture(
        schemaType: SchemaType,
        startTimeMs: Long,
        name: String,
        autoTerminate: Boolean,
        private: Boolean,
    ): SpanToken {
        val mode = when {
            autoTerminate -> AutoTerminationMode.ON_BACKGROUND
            else -> AutoTerminationMode.NONE
        }
        val span = spanService.startSpan(
            name = name,
            startTimeMs = startTimeMs,
            autoTerminationMode = mode,
            private = private,
            type = schemaType.telemetryType
        ).apply {
            schemaType.attributes().forEach {
                addAttribute(it.key, it.value)
            }
            sessionUpdateAction?.invoke()
        }
        return SpanTokenImpl(span) {
            sessionUpdateAction?.invoke()
        }
    }

    override fun startSpanCapture(
        name: String,
        startTimeMs: Long,
        parent: SpanToken?,
        type: EmbType,
        private: Boolean,
    ): SpanToken {
        val parentRef = retrieveParentReference(parent)
        val span = spanService.startSpan(
            name = name,
            startTimeMs = startTimeMs,
            parent = parentRef,
            private = private,
            type = type,
        )
        return SpanTokenImpl(span) {
            sessionUpdateAction?.invoke()
        }
    }

    override fun <T : Any> startSessionPartStateCapture(state: SchemaType.State<T>): SessionPartStateToken<T> {
        val spanToken = startSpanCapture(
            schemaType = state,
            startTimeMs = clock.now(),
            private = true
        )

        return SessionPartStateTokenImpl(
            spanToken = spanToken
        )
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCodeAttribute?,
        parent: SpanToken?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<SpanEvent>,
    ) {
        val parentRef = retrieveParentReference(parent)
        spanService.recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            errorCode = errorCode.toErrorCode(),
            type = type,
            parent = parentRef,
            internal = internal,
            private = private,
            attributes = attributes,
            events = events.mapNotNull(::toEmbraceSpanEvent),
        )
        sessionUpdateAction?.invoke()
    }

    override fun addSessionPartEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        val currentSession = currentSessionPartSpan.current() ?: return false
        return currentSession.addSystemEvent(
            schemaType.fixedObjectName.toEmbraceObjectName(),
            startTimeMs,
            schemaType.attributes() + schemaType.telemetryType.asPair()
        ).also {
            sessionUpdateAction?.invoke()
        }
    }

    override fun removeSessionPartEvents(type: EmbType) {
        val currentSession = currentSessionPartSpan.current() ?: return
        currentSession.removeSystemEvents(type)
        sessionUpdateAction?.invoke()
    }

    override fun addSessionPartAttribute(key: String, value: String) {
        val currentSession = currentSessionPartSpan.current() ?: return
        currentSession.addSystemAttribute(key, value)
        sessionUpdateAction?.invoke()
    }

    override fun removeSessionPartAttribute(key: String) {
        val currentSession = currentSessionPartSpan.current() ?: return
        currentSession.removeSystemAttribute(key)
        sessionUpdateAction?.invoke()
    }

    private fun retrieveParentReference(parent: SpanToken?): EmbraceSpan? = (parent as? SpanTokenImpl)?.span

    private fun toEmbraceSpanEvent(event: SpanEvent): EmbraceSpanEvent? {
        return EmbraceSpanEvent.create(event.name, event.timestampNanos.nanosToMillis(), event.attributes)
    }

    private fun getSeverityText(severity: SeverityNumber) = when (severity) {
        SeverityNumber.WARN -> "WARNING"
        else -> severity.name
    }

    private class SpanTokenImpl(
        val span: EmbraceSdkSpan,
        private val sessionUpdateAction: () -> Unit?,
    ) : SpanToken {
        override fun stop(endTimeMs: Long?, errorCode: ErrorCodeAttribute?) {
            span.stop(endTimeMs = endTimeMs, errorCode = errorCode?.toErrorCode())
            sessionUpdateAction.invoke()
        }

        override fun isRecording() = span.isRecording

        override fun isValid() = span.spanContext?.isValid == true

        override fun addAttribute(key: String, value: String) {
            span.addAttribute(key, value)
        }

        override fun getStartTimeMs(): Long? = span.getStartTimeMs()

        override fun asW3cTraceparent(): String? = span.asW3cTraceParent()

        override fun setSystemAttribute(key: String, value: String) = span.setSystemAttribute(key, value)

        override fun addEvent(name: String, eventTimeMs: Long, attributes: Map<String, String>) {
            span.addEvent(
                name = name,
                timestampMs = eventTimeMs,
                attributes = attributes
            )
        }
    }

    private class SessionPartStateTokenImpl<T : Any>(
        private val spanToken: SpanToken,
    ) : SessionPartStateToken<T> {
        private val transitionCount = AtomicInteger(0)

        override fun update(
            updateDetectedTimeMs: Long,
            newValue: T,
            unrecordedTransitions: UnrecordedTransitions,
        ): Boolean {
            if (!spanToken.isRecording()) {
                return false
            }
            spanToken.addEvent(
                name = "transition",
                eventTimeMs = updateDetectedTimeMs,
                attributes = mutableMapOf(EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE to newValue.toString())
                    .apply {
                        if (unrecordedTransitions.notInSession > 0) {
                            setEmbraceAttribute(EmbStateTransitionAttributes.EMB_STATE_NOT_IN_SESSION, unrecordedTransitions.notInSession)
                        }

                        if (unrecordedTransitions.droppedByInstrumentation > 0) {
                            setEmbraceAttribute(
                                EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION,
                                unrecordedTransitions.droppedByInstrumentation
                            )
                        }
                    }.toMap()
            )
            spanToken.setSystemAttribute(
                EmbStateTransitionAttributes.EMB_STATE_TRANSITION_COUNT,
                transitionCount.incrementAndGet().toString()
            )
            return true
        }

        override fun end(unrecordedTransitions: UnrecordedTransitions) {
            if (unrecordedTransitions.notInSession > 0) {
                spanToken.setSystemAttribute(
                    EmbStateTransitionAttributes.EMB_STATE_NOT_IN_SESSION,
                    unrecordedTransitions.notInSession.toString()
                )
            }
            if (unrecordedTransitions.droppedByInstrumentation > 0) {
                spanToken.setSystemAttribute(
                    EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION,
                    unrecordedTransitions.droppedByInstrumentation.toString()
                )
            }
            spanToken.stop()
        }
    }
}

internal fun ErrorCodeAttribute?.toErrorCode(): ErrorCode? {
    return when (this) {
        ErrorCodeAttribute.Failure -> ErrorCode.FAILURE
        ErrorCodeAttribute.Unknown -> ErrorCode.UNKNOWN
        ErrorCodeAttribute.UserAbandon -> ErrorCode.USER_ABANDON
        else -> null
    }
}
