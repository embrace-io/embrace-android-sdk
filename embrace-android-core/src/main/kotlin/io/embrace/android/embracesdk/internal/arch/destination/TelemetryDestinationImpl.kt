package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService.Companion.BACKGROUND_STATE
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService.Companion.FOREGROUND_STATE
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class)
internal class TelemetryDestinationImpl(
    private val logger: Logger,
    private val sessionIdTracker: SessionIdTracker,
    private val processStateService: ProcessStateService,
    private val clock: Clock,
    private val spanService: SpanService,
    private val currentSessionSpan: CurrentSessionSpan,
) : TelemetryDestination {

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
            setStringAttribute(LogAttributes.LOG_RECORD_UID, Uuid.getEmbUuid())

            if (addCurrentSessionInfo) {
                var sessionState: String? = null
                sessionIdTracker.getActiveSession()?.let { session ->
                    if (session.id.isNotBlank()) {
                        setStringAttribute(SessionAttributes.SESSION_ID, session.id)
                    }
                    sessionState = if (session.isForeground) {
                        FOREGROUND_STATE
                    } else {
                        BACKGROUND_STATE
                    }
                }
                setStringAttribute(embState.name, sessionState ?: processStateService.getAppState())
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
        } ?: return null
        return SpanTokenImpl(span)
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: String?,
        type: EmbType,
        attributes: Map<String, String>,
    ) {
        spanService.recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            errorCode = errorCode?.let {
                ErrorCode.valueOf(it)
            },
            type = type,
            attributes = attributes,
        )
    }

    override fun addSessionEvent(schemaType: SchemaType, startTimeMs: Long): Boolean {
        val currentSession = currentSessionSpan.current() ?: return false
        return currentSession.addSystemEvent(
            schemaType.fixedObjectName.toEmbraceObjectName(),
            startTimeMs,
            schemaType.attributes() + schemaType.telemetryType.asPair()
        )
    }

    override fun removeSessionEvents(type: EmbType) {
        val currentSession = currentSessionSpan.current() ?: return
        currentSession.removeSystemEvents(type)
    }

    override fun addSessionAttribute(key: String, value: String) {
        val currentSession = currentSessionSpan.current() ?: return
        currentSession.addSystemAttribute(key, value)
    }

    override fun removeSessionAttribute(key: String) {
        val currentSession = currentSessionSpan.current() ?: return
        currentSession.removeSystemAttribute(key)
    }

    private fun getSeverityText(severity: SeverityNumber) = when (severity) {
        SeverityNumber.WARN -> "WARNING"
        else -> severity.name
    }

    private class SpanTokenImpl(private val span: EmbraceSpan) : SpanToken {
        override fun stop(endTimeMs: Long?) {
            span.stop(endTimeMs = endTimeMs)
        }
    }
}
