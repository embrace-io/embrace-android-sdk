package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.opentelemetry.embState
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
        severity: SeverityNumber,
        message: String,
        isPrivate: Boolean,
        addCurrentSessionInfo: Boolean,
        timestampMs: Long?,
    ) {
        val logTimeMs = timestampMs ?: clock.now()
        logger.log(
            body = message,
            severityNumber = severity,
            severityText = getSeverityText(severity),
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
                setStringAttribute(embState.attributeKey, sessionState ?: processStateService.getAppState())
            }

            if (isPrivate) {
                setStringAttribute(PrivateSpan.key.attributeKey, PrivateSpan.value)
            }

            with(schemaType) {
                setStringAttribute(telemetryType.key.attributeKey, telemetryType.value)
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
