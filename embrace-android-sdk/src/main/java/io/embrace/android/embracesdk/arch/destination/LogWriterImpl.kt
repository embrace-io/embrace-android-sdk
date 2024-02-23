package io.embrace.android.embracesdk.arch.destination

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import java.util.concurrent.TimeUnit

internal class LogWriterImpl(private val logger: Logger) : LogWriter {

    override fun addLog(log: LogEventData) {
        val builder = logger.logRecordBuilder()
            .setBody(log.message)
            .setSeverity(log.severity)
            .setSeverityText(log.severityText ?: log.severity.name)
            .setTimestamp(log.timestampNanos, TimeUnit.MILLISECONDS)

        log.attributes?.forEach {
            builder.setAttribute(AttributeKey.stringKey(it.key), it.value)
        }

        builder.emit()
    }
}
