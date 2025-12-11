package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SessionStateToken
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalApi::class)
class TelemetryDestinationImpl(
    private val logger: Logger,
    private val sessionTracker: SessionTracker,
    private val appStateTracker: AppStateTracker,
    private val clock: Clock,
    private val spanService: SpanService,
    private val currentSessionSpan: CurrentSessionSpan,
) : TelemetryDestination {

    override var sessionUpdateAction: (() -> Unit)? = null

    @OptIn(IncubatingApi::class)
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
        logger.log(
            body = message,
            severityNumber = severityNumber,
            severityText = getSeverityText(severityNumber),
            timestamp = TimeUnit.MILLISECONDS.toNanos(logTimeMs)
        ) {
            if (!attributes.contains(LogAttributes.LOG_RECORD_UID)) {
                setStringAttribute(LogAttributes.LOG_RECORD_UID, Uuid.getEmbUuid())
            }

            if (addCurrentSessionInfo) {
                var sessionState: AppState? = null
                sessionTracker.getActiveSession()?.let { session ->
                    if (session.sessionId.isNotBlank()) {
                        setStringAttribute(SessionAttributes.SESSION_ID, session.sessionId)
                    }
                    sessionState = session.appState
                }
                val state = sessionState ?: appStateTracker.getAppState()
                setStringAttribute(embState.name, state.description)
            }

            if (isPrivate) {
                setStringAttribute(PrivateSpan.key.name, PrivateSpan.value)
            }

            with(schemaType) {
                setStringAttribute(telemetryType.key.name, telemetryType.value)
                attributes().forEach {
                    setStringAttribute(it.key, it.value)
                }
            }
            sessionUpdateAction?.invoke()
        }
    }

    override fun startSpanCapture(
        schemaType: SchemaType,
        startTimeMs: Long,
        autoTerminate: Boolean,
    ): SpanToken? {
        val mode = when {
            autoTerminate -> AutoTerminationMode.ON_BACKGROUND
            else -> AutoTerminationMode.NONE
        }
        val span = spanService.startSpan(
            name = schemaType.fixedObjectName,
            startTimeMs = startTimeMs,
            autoTerminationMode = mode,
            type = schemaType.telemetryType
        )?.apply {
            schemaType.attributes().forEach {
                addAttribute(it.key, it.value)
            }
            sessionUpdateAction?.invoke()
        } ?: return null
        return SpanTokenImpl(span) {
            sessionUpdateAction?.invoke()
        }
    }

    override fun startSpanCapture(
        name: String,
        startTimeMs: Long,
        parent: SpanToken?,
        type: EmbType,
    ): SpanToken? {
        val parentRef = retrieveParentReference(parent)
        val span = spanService.startSpan(
            name = name,
            startTimeMs = startTimeMs,
            parent = parentRef,
            type = type,
        ) ?: return null
        return SpanTokenImpl(span) {
            sessionUpdateAction?.invoke()
        }
    }

    override fun <T> startSessionStateCapture(state: SchemaType.State<T>): SessionStateToken<T> {
        val spanToken = startSpanCapture(
            schemaType = state,
            startTimeMs = clock.now(),
            autoTerminate = false
        ) ?: return NoopSessionStateToken()

        return SessionStateTokenImpl(
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

    override fun addSessionEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        val currentSession = currentSessionSpan.current() ?: return false
        return currentSession.addSystemEvent(
            schemaType.fixedObjectName.toEmbraceObjectName(),
            startTimeMs,
            schemaType.attributes() + schemaType.telemetryType.asPair()
        ).also {
            sessionUpdateAction?.invoke()
        }
    }

    override fun removeSessionEvents(type: EmbType) {
        val currentSession = currentSessionSpan.current() ?: return
        currentSession.removeSystemEvents(type)
        sessionUpdateAction?.invoke()
    }

    override fun addSessionAttribute(key: String, value: String) {
        val currentSession = currentSessionSpan.current() ?: return
        currentSession.addSystemAttribute(key, value)
        sessionUpdateAction?.invoke()
    }

    override fun removeSessionAttribute(key: String) {
        val currentSession = currentSessionSpan.current() ?: return
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

        override fun addAttribute(key: String, value: String) {
            span.addAttribute(key, value)
        }

        override fun getStartTimeMs(): Long? = span.getStartTimeMs()

        override fun setSystemAttribute(key: String, value: String) = span.setSystemAttribute(key, value)

        override fun addEvent(name: String, eventTimeMs: Long, attributes: Map<String, String>) {
            span.addEvent(
                name = name,
                timestampMs = eventTimeMs,
                attributes = attributes
            )
        }
    }

    private class SessionStateTokenImpl<T>(
        private val spanToken: SpanToken,
    ) : SessionStateToken<T> {
        private val transitionCount = AtomicInteger(0)

        override fun update(updateDetectedTimeMs: Long, newValue: T) {
            spanToken.addEvent(
                name = "transition",
                eventTimeMs = updateDetectedTimeMs,
                attributes = mapOf(
                    "new_state" to newValue.toString()
                )
            )
            spanToken.setSystemAttribute("transition_count", transitionCount.incrementAndGet().toString())
        }

        override fun end() {
            spanToken.stop()
        }
    }

    private class NoopSessionStateToken<T> : SessionStateToken<T> {
        override fun update(updateDetectedTimeMs: Long, newValue: T) {}
        override fun end() {}
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
