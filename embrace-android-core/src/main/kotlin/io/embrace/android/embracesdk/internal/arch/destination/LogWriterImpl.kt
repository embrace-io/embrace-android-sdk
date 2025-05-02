package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService.Companion.BACKGROUND_STATE
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService.Companion.FOREGROUND_STATE
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.spans.setAttribute
import io.embrace.android.embracesdk.internal.spans.setFixedAttribute
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.TimeUnit

class LogWriterImpl(
    private val logger: Logger,
    private val sessionIdTracker: SessionIdTracker,
    private val processStateService: ProcessStateService,
    private val clock: Clock
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
        val builder = logger.logRecordBuilder()
            .setBody(message)
            .setSeverity(severity)
            .setSeverityText(getSeverityText(severity))
            .setTimestamp(logTimeMs, TimeUnit.MILLISECONDS)

        builder.setAttribute(LogIncubatingAttributes.LOG_RECORD_UID, Uuid.getEmbUuid())

        if (addCurrentSessionInfo) {
            var sessionState: String? = null
            sessionIdTracker.getActiveSession()?.let { session ->
                builder.setAttribute(SessionIncubatingAttributes.SESSION_ID, session.id, false)
                sessionState = if (session.isForeground) {
                    FOREGROUND_STATE
                } else {
                    BACKGROUND_STATE
                }
            }

            builder.setAttribute(embState.attributeKey, sessionState ?: processStateService.getAppState())
        }

        if (isPrivate) {
            builder.setFixedAttribute(PrivateSpan)
        }

        with(schemaType) {
            builder.setFixedAttribute(telemetryType)
            attributes().forEach {
                builder.setAttribute(AttributeKey.stringKey(it.key), it.value)
            }
        }

        builder.emit()
    }

    private fun getSeverityText(severity: Severity) = when (severity) {
        Severity.WARN -> "WARNING"
        else -> severity.name
    }
}
