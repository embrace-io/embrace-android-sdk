package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.spans.setFixedAttribute
import io.embrace.android.embracesdk.internal.spans.toOtelSeverity
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.opentelemetry.embState
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes

internal class LogWriterImpl(
    private val logger: Logger,
    private val sessionIdTracker: SessionIdTracker,
    private val metadataService: MetadataService,
) : LogWriter {

    override fun addLog(
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean
    ) {
        val builder = logger.logRecordBuilder()
            .setBody(message)
            .setSeverity(severity.toOtelSeverity())
            .setSeverityText(severity.name)

        builder.setAttribute(LogIncubatingAttributes.LOG_RECORD_UID, Uuid.getEmbUuid())

        sessionIdTracker.getActiveSessionId()?.let { sessionId ->
            builder.setAttribute(SessionIncubatingAttributes.SESSION_ID, sessionId)
        }

        metadataService.getAppState()?.let { appState ->
            builder.setAttribute(embState.attributeKey, appState)
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
}
