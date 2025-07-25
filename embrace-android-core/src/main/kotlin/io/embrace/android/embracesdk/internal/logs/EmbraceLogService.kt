package io.embrace.android.embracesdk.internal.logs

import android.os.Parcelable
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Exception
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.FlutterException
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Log
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment
import io.embrace.android.embracesdk.internal.otel.attrs.embExceptionHandling
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.utils.PropertyUtils.truncate
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import java.io.Serializable

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
class EmbraceLogService(
    private val logWriter: LogWriter,
    private val configService: ConfigService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val payloadStore: PayloadStore?,
) : LogService {

    private val behavior = configService.logMessageBehavior
    private val bypassLimitsValidation = configService.isOnlyUsingOtelExporters()
    private val logCounters = mapOf(
        Severity.INFO to LogCounter(behavior::getInfoLogLimit),
        Severity.WARNING to LogCounter(behavior::getWarnLogLimit),
        Severity.ERROR to LogCounter(behavior::getErrorLogLimit)
    )

    override fun log(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        customLogAttrs: Map<String, String>,
        logAttachment: Attachment.EmbraceHosted?,
    ) {
        val redactedProperties = redactSensitiveProperties(sanitizeProperties(properties, bypassLimitsValidation))
        val attrs = createTelemetryAttributes(redactedProperties, customLogAttrs)

        val schemaProvider: (TelemetryAttributes) -> SchemaType = when {
            logExceptionType == LogExceptionType.NONE -> ::Log
            configService.appFramework == AppFramework.FLUTTER -> ::FlutterException
            else -> ::Exception
        }
        if (logExceptionType != LogExceptionType.NONE) {
            attrs.setAttribute(embExceptionHandling, logExceptionType.value)
        }

        logAttachment?.let {
            val envelope = Envelope(data = Pair(it.id, it.bytes))
            payloadStore?.storeAttachment(envelope)
        }

        addLogEventData(
            message = message,
            severity = severity,
            attributes = attrs,
            schemaProvider = schemaProvider
        )
    }

    override fun getErrorLogsCount(): Int {
        return logCounters.getValue(Severity.ERROR).getCount()
    }

    override fun cleanCollections() {
        logCounters.forEach { it.value.clear() }
    }

    /**
     * Create [TelemetryAttributes] with the standard log properties
     */
    private fun createTelemetryAttributes(
        customProperties: Map<String, Any>?,
        logAttrs: Map<String, String>,
    ): TelemetryAttributes {
        val attributes = TelemetryAttributes(
            sessionPropertiesProvider = if (!configService.sessionBehavior.shouldGateSessionProperties()) {
                sessionPropertiesService::getProperties
            } else {
                { null }
            },
            customAttributes = if (!configService.sessionBehavior.shouldGateLogProperties()) {
                customProperties?.mapValues { it.value.toString() } ?: emptyMap()
            } else {
                null
            },
        )
        attributes.setAttribute(LogIncubatingAttributes.LOG_RECORD_UID.key, Uuid.getEmbUuid())
        logAttrs.forEach {
            attributes.setAttribute(it.key, it.value)
        }
        return attributes
    }

    private fun addLogEventData(
        message: String,
        severity: Severity,
        attributes: TelemetryAttributes,
        schemaProvider: (TelemetryAttributes) -> SchemaType,
    ) {
        if (shouldLogBeGated(severity)) {
            return
        }
        val logId = attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID.key)
        if (logId == null || !logCounters.getValue(severity).addIfAllowed()) {
            return
        }
        logWriter.addLog(schemaProvider(attributes), severity, trimToMaxLength(message))
    }

    /**
     * Checks if the info or warning log event should be gated based on gating config. Error logs
     * should never be gated.
     *
     * @param severity of the log event
     * @return true if the log should be gated
     */
    private fun shouldLogBeGated(severity: Severity): Boolean {
        return when (severity) {
            Severity.INFO -> configService.sessionBehavior.shouldGateInfoLog()
            Severity.WARNING -> configService.sessionBehavior.shouldGateWarnLog()
            else -> false
        }
    }

    private fun trimToMaxLength(message: String): String {
        val maxLength = if (configService.appFramework == AppFramework.UNITY) {
            LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH
        } else {
            behavior.getLogMessageMaximumAllowedLength()
        }

        if (message.length > maxLength) {
            val endChars = "..."

            if (maxLength <= endChars.length) {
                return message.take(maxLength)
            }
            return message.take(maxLength - endChars.length) + endChars
        } else {
            return message
        }
    }

    private fun redactSensitiveProperties(properties: Map<String, Any>?): Map<String, Any>? {
        return properties?.mapValues { (key, value) ->
            if (configService.sensitiveKeysBehavior.isSensitiveKey(key)) REDACTED_LABEL else value
        }
    }

    private fun sanitizeProperties(
        properties: Map<String, Any>?,
        bypassPropertyLimit: Boolean = false,
    ): Map<String, Any> {
        return if (properties == null) {
            emptyMap()
        } else {
            runCatching {
                if (bypassPropertyLimit) {
                    properties.entries.associate {
                        Pair(it.key, checkIfSerializable(it.value))
                    }
                } else {
                    properties.entries.take(MAX_PROPERTY_COUNT).associate {
                        Pair(
                            first = truncate(it.key, MAX_PROPERTY_KEY_LENGTH),
                            second = truncate(checkIfSerializable(it.value).toString(), MAX_PROPERTY_VALUE_LENGTH)
                        )
                    }
                }
            }.getOrDefault(emptyMap())
        }
    }

    private fun checkIfSerializable(value: Any): Any {
        if (!(value is Parcelable || value is Serializable)) {
            return "not serializable"
        }
        return value
    }

    private companion object {

        /**
         * The default limit of Unity log messages that can be sent.
         */
        private const val LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH = 16384

        private const val MAX_PROPERTY_COUNT: Int = 100

        private const val MAX_PROPERTY_KEY_LENGTH = 128

        private const val MAX_PROPERTY_VALUE_LENGTH = 1024
    }
}
