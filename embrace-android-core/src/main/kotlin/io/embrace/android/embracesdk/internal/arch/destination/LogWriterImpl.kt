package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.otel.attrs.embState
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService.Companion.BACKGROUND_STATE
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService.Companion.FOREGROUND_STATE
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class)
class LogWriterImpl(
    private val logger: Logger,
    private val sessionIdTracker: SessionIdTracker,
    private val processStateService: ProcessStateService,
    private val clock: Clock,
) : LogWriter {

    override fun addLog(
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean,
        addCurrentSessionInfo: Boolean,
        timestampMs: Long?,
    ) {
        val logTimeMs = timestampMs ?: clock.now()
        val severityNumber = when (severity) {
            Severity.INFO -> SeverityNumber.INFO
            Severity.WARNING -> SeverityNumber.WARN
            Severity.ERROR -> SeverityNumber.ERROR
        }
        logger.log(
            body = message,
            severityNumber = severityNumber,
            severityText = getSeverityText(severityNumber),
            timestampNs = TimeUnit.MILLISECONDS.toNanos(logTimeMs)
        ) {
            setStringAttribute(LogIncubatingAttributes.LOG_RECORD_UID.key, Uuid.getEmbUuid())

            if (addCurrentSessionInfo) {
                var sessionState: String? = null
                sessionIdTracker.getActiveSession()?.let { session ->
                    if (session.id.isNotBlank()) {
                        setStringAttribute(SessionIncubatingAttributes.SESSION_ID.key, session.id)
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

    private fun getSeverityText(severity: SeverityNumber) = when (severity) {
        SeverityNumber.WARN -> "WARNING"
        else -> severity.name
    }
}
