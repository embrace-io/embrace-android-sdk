package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class, IncubatingApi::class)
class EventServiceImpl(
    loggerProvider: Provider<Logger>
) : EventService {

    private val logger: Logger = loggerProvider()

    override fun log(
        logTimeMs: Long,
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean,
        embraceAttributes: Map<String, String>
    ) {
        val severityNumber = when (severity) {
            Severity.INFO -> SeverityNumber.INFO
            Severity.WARNING -> SeverityNumber.WARN
            Severity.ERROR -> SeverityNumber.ERROR
        }
        val baseAttributes = embraceAttributes.toMap()
        logger.log(
            body = message,
            severityNumber = severityNumber,
            severityText = getSeverityText(severityNumber),
            timestamp = TimeUnit.MILLISECONDS.toNanos(logTimeMs)
        ) {
            if (!baseAttributes.contains(LogAttributes.LOG_RECORD_UID)) {
                setStringAttribute(LogAttributes.LOG_RECORD_UID, Uuid.getEmbUuid())
            }

            if (isPrivate) {
                setStringAttribute(PrivateSpan.key.name, PrivateSpan.value)
            }

            with(schemaType) {
                setStringAttribute(telemetryType.key.name, telemetryType.value)
                attributes().forEach {
                    setStringAttribute(it.key, it.value)
                }
            }

            baseAttributes.forEach {
                setStringAttribute(it.key, it.value)
            }
        }
    }

    private fun getSeverityText(severity: SeverityNumber) = when (severity) {
        SeverityNumber.WARN -> "WARNING"
        else -> severity.name
    }
}
