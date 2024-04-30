package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.spans.setFixedAttribute
import io.embrace.android.embracesdk.internal.spans.toOtelSeverity
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.opentelemetry.embState
import io.embrace.android.embracesdk.opentelemetry.logRecordUid
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger

internal class LogWriterImpl(
    private val logger: Logger,
    private val sessionIdTracker: SessionIdTracker,
    private val metadataService: MetadataService,
) : LogWriter {

    override fun <T> addLog(log: T, mapper: (T.() -> LogEventData)?, isPrivate: Boolean) {
        val logEventData = if (log is LogEventData) {
            log
        } else if (mapper != null) {
            log.mapper()
        } else {
            return
        }

        val builder = logger.logRecordBuilder()
            .setBody(logEventData.message)
            .setSeverity(logEventData.severity.toOtelSeverity())
            .setSeverityText(logEventData.severity.name)

        builder.setAttribute(logRecordUid, Uuid.getEmbUuid())

        sessionIdTracker.getActiveSessionId()?.let { sessionId ->
            builder.setAttribute(embSessionId.attributeKey, sessionId)
        }

        metadataService.getAppState()?.let { appState ->
            builder.setAttribute(embState.attributeKey, appState)
        }

        if (isPrivate) {
            builder.setFixedAttribute(PrivateSpan)
        }

        logEventData.schemaType.attributes().forEach {
            builder.setAttribute(AttributeKey.stringKey(it.key), it.value)
        }

        builder.emit()
    }
}
