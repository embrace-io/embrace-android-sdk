package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.spans.setFixedAttribute
import io.embrace.android.embracesdk.internal.spans.toOtelSeverity
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.opentelemetry.embState
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes

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
            builder.setAttribute(embSessionId.attributeKey, sessionId)
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