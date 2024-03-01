package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.worker.BackgroundWorker

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal class EmbraceLogService(
    private val logWriter: LogWriter,
    private val clock: Clock,
    private val metadataService: MetadataService,
    private val sessionIdTracker: SessionIdTracker,
    private val backgroundWorker: BackgroundWorker
) : LogService {

    override fun log(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?
    ) {
        addLogEventData(message, severity, EmbraceLogAttributes(properties))
    }

    override fun logException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    ) {
        val attributes = EmbraceLogAttributes(properties)
        attributes.setExceptionType(logExceptionType)
        // TBD: Add stacktrace elements
        exceptionName?.let { attributes.setExceptionName(it) }
        exceptionMessage?.let { attributes.setExceptionMessage(it) }
        context?.let { attributes.setExceptionContext(it) }
        library?.let { attributes.setExceptionLibrary(it) }
        addLogEventData(message, severity, attributes)
    }

    private fun addLogEventData(
        message: String,
        severity: Severity,
        attributes: EmbraceLogAttributes,
    ) {
        backgroundWorker.submit {
            // TBD: Check if log should be gated
            // TBD: Count log and enforce limits

            // Set these after the custom properties so they can't be overridden
            sessionIdTracker.getActiveSessionId()?.let { attributes.setSessionId(it) }
            metadataService.getAppState()?.let { attributes.setAppState(it) }
            attributes.setLogId(Uuid.getEmbUuid())

            val otelSeverity = mapSeverity(severity)
            val logEventData = LogEventData(
                startTimeMs = clock.nowInNanos(),
                message = message,
                severity = otelSeverity,
                severityText = otelSeverity.name,
                attributes = attributes.toMap()
            )

            logWriter.addLog(logEventData) { logEventData }
        }
    }

    private fun mapSeverity(embraceSeverity: Severity): io.opentelemetry.api.logs.Severity {
        return when (embraceSeverity) {
            Severity.INFO -> io.opentelemetry.api.logs.Severity.INFO
            Severity.WARNING -> io.opentelemetry.api.logs.Severity.WARN
            Severity.ERROR -> io.opentelemetry.api.logs.Severity.ERROR
        }
    }
}
