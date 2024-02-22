package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import java.util.concurrent.TimeUnit

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
internal class EmbraceLogService(
    private val logger: Logger,
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
        val sessionId = sessionIdTracker.getActiveSessionId()
        val appState = metadataService.getAppState()

        backgroundWorker.submit {
            // TBD: Check if config enabled
            // TBD: Check if log should be gated
            // TBD: Count log and enforce limits

            emitLog(
                message,
                severity,
                sessionId,
                appState,
                properties
            )
        }
    }

    override fun logException(
        message: String,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    ) {
        val sessionId = sessionIdTracker.getActiveSessionId()
        val appState = metadataService.getAppState()

        backgroundWorker.submit {
            // TBD: Check if config enabled
            // TBD: Check if log should be gated
            // TBD: Count log and enforce limits

            emitLog(
                message,
                Severity.ERROR,
                sessionId,
                appState,
                properties,
                logExceptionType,
                exceptionName,
                exceptionMessage,
                context,
                library
            )
        }
    }

    private fun emitLog(
        message: String,
        severity: Severity,
        sessionId: String?,
        appState: String?,
        properties: Map<String, Any>?,
        exceptionType: LogExceptionType? = null,
        exceptionName: String? = null,
        exceptionMessage: String? = null,
        context: String? = null,
        library: String? = null
    ) {
        val otelSeverity = mapSeverity(severity)

        val builder = logger.logRecordBuilder()
            .setBody(message)
            .setSeverity(otelSeverity)
            .setSeverityText(otelSeverity.name)
            .setTimestamp(clock.now(), TimeUnit.MILLISECONDS)

        properties?.forEach {
            builder.setAttribute(AttributeKey.stringKey(it.key), it.value.toString())
        }

        // Set these after the custom properties so they can't be overridden
        appState?.let { builder.setAppState(it) }
        sessionId?.let { builder.setSessionId(it) }
        exceptionType?.let { builder.setExceptionType(it) }
        exceptionName?.let { builder.setExceptionName(it) }
        exceptionMessage?.let { builder.setExceptionMessage(it) }
        context?.let { builder.setExceptionContext(it) }
        library?.let { builder.setExceptionLibrary(it) }

        builder
            .setEventId(Uuid.getEmbUuid())
            .emit()
    }

    private fun mapSeverity(embraceSeverity: Severity): io.opentelemetry.api.logs.Severity {
        return when (embraceSeverity) {
            Severity.INFO -> io.opentelemetry.api.logs.Severity.INFO
            Severity.WARNING -> io.opentelemetry.api.logs.Severity.WARN
            Severity.ERROR -> io.opentelemetry.api.logs.Severity.ERROR
        }
    }
}
