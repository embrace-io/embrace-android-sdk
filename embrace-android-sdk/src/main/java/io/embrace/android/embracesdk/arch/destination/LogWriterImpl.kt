package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.opentelemetry.embState
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity

internal class LogWriterImpl(
    private val logger: Logger,
    private val sessionIdTracker: SessionIdTracker,
    private val metadataService: MetadataService,
) : LogWriter {

    override fun <T> addLog(log: T, mapper: (T.() -> LogEventData)?) {
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

        logEventData.schemaType.attributes().forEach {
            builder.setAttribute(AttributeKey.stringKey(it.key), it.value)
        }

        sessionIdTracker.getActiveSessionId()?.let { sessionId ->
            builder.setAttribute(embSessionId.attributeKey, sessionId)
        }

        metadataService.getAppState()?.let { appState ->
            builder.setAttribute(embState.attributeKey, appState)
        }

        builder.emit()
    }

    private fun io.embrace.android.embracesdk.Severity.toOtelSeverity(): Severity = when (this) {
        io.embrace.android.embracesdk.Severity.INFO -> Severity.INFO
        io.embrace.android.embracesdk.Severity.WARNING -> Severity.WARN
        io.embrace.android.embracesdk.Severity.ERROR -> Severity.ERROR
    }
}
