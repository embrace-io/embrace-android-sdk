package io.embrace.android.embracesdk.arch.destination

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import java.util.concurrent.TimeUnit

internal class LogWriterImpl(private val logger: Logger) : LogWriter {

    override fun <T> addLog(log: T, mapper: T.() -> LogEventData) {
        val logEventData = log.mapper()

        // placeholder implementation: copied from PR #460
        val builder = logger.logRecordBuilder()
            .setBody(logEventData.message)
            .setSeverity(logEventData.severity.toOtelSeverity())
            .setSeverityText(logEventData.severity.name)
            .setTimestamp(logEventData.startTimeMs, TimeUnit.MILLISECONDS)

        logEventData.attributes?.forEach {
            builder.setAttribute(AttributeKey.stringKey(it.key), it.value)
        }
        builder.emit()
    }

    private fun io.embrace.android.embracesdk.Severity.toOtelSeverity(): Severity = when (this) {
        io.embrace.android.embracesdk.Severity.INFO -> Severity.INFO
        io.embrace.android.embracesdk.Severity.WARNING -> Severity.WARN
        io.embrace.android.embracesdk.Severity.ERROR -> Severity.ERROR
    }
}
