package io.embrace.android.embracesdk.arch.destination

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Logger
import java.util.concurrent.TimeUnit

internal class LogWriterImpl(private val logger: Logger) : LogWriter {

    override fun <T> addLog(log: T, mapper: T.() -> LogEventData) {
        val logEventData = log.mapper()
        val builder = logger.logRecordBuilder()
            .setBody(logEventData.message)
            .setSeverity(logEventData.severity)
            .setSeverityText(logEventData.severityText)
            .setTimestamp(logEventData.startTimeMs, TimeUnit.MILLISECONDS)

        logEventData.attributes?.forEach {
            builder.setAttribute(AttributeKey.stringKey(it.key), it.value)
        }

        builder.emit()
    }
}
