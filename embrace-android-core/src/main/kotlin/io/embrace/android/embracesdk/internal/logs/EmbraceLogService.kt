package io.embrace.android.embracesdk.internal.logs

import android.os.Parcelable
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.attrs.embExceptionHandling
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Exception
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.FlutterException
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Log
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment.EmbraceHosted
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.utils.PropertyUtils.truncate
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import java.io.Serializable

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
@OptIn(IncubatingApi::class)
class EmbraceLogService(
    private val destination: TelemetryDestination,
    private val configService: ConfigService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val payloadStore: PayloadStore?,
    private val serializer: PlatformSerializer,
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
        attributes: Map<String, Any>,
        embraceAttributes: Map<String, String>,
        attachment: Attachment?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        exceptionName: String?,
        exceptionMessage: String?,
    ) {
        val attrs = mutableMapOf<String, String>()
        exceptionName?.let { attrs[ExceptionAttributes.EXCEPTION_TYPE] = it }
        exceptionMessage?.let { attrs[ExceptionAttributes.EXCEPTION_MESSAGE] = it }

        val stacktrace =
            stackTraceElements?.let(serializer::truncatedStacktrace) ?: customStackTrace
        stacktrace?.let { attrs[ExceptionAttributes.EXCEPTION_STACKTRACE] = it }

        if (attachment != null) {
            attrs.putAll(attachment.attributes.mapKeys { it.key.name })
        }

        val logAttachment = when {
            attachment is EmbraceHosted && attachment.shouldAttemptUpload() -> attachment
            else -> null
        }

        val finalAttrs = if (logExceptionType != LogExceptionType.NONE) {
            attributes.plus(embExceptionHandling.name to logExceptionType.value)
        } else {
            attributes
        }

        val redactedProperties =
            redactSensitiveProperties(
                sanitizeProperties(
                    properties = finalAttrs,
                    bypassPropertyLimit = bypassLimitsValidation
                )
            )
        val telemetryAttributes = TelemetryAttributes(
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = redactedProperties.mapValues { it.value.toString() },
        )
        telemetryAttributes.setAttribute(LogAttributes.LOG_RECORD_UID, Uuid.getEmbUuid())
        attrs.plus(embraceAttributes).forEach {
            telemetryAttributes.setAttribute(it.key, it.value)
        }

        val schemaProvider: (TelemetryAttributes) -> SchemaType = when {
            logExceptionType == LogExceptionType.NONE -> ::Log
            configService.appFramework == AppFramework.FLUTTER -> ::FlutterException
            else -> ::Exception
        }
        logAttachment?.let {
            val envelope = Envelope(data = Pair(it.id, it.bytes))
            payloadStore?.storeAttachment(envelope)
        }

        val logId = telemetryAttributes.getAttribute(LogAttributes.LOG_RECORD_UID)
        if (logId == null || !logCounters.getValue(severity).addIfAllowed()) {
            return
        }
        val logSeverity = when (severity) {
            Severity.INFO -> LogSeverity.INFO
            Severity.WARNING -> LogSeverity.WARNING
            Severity.ERROR -> LogSeverity.ERROR
        }
        destination.addLog(schemaProvider(telemetryAttributes), logSeverity, trimToMaxLength(message))
    }

    override fun getErrorLogsCount(): Int {
        return logCounters.getValue(Severity.ERROR).getCount()
    }

    override fun onPostSessionChange() {
        logCounters.forEach { it.value.clear() }
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

    private fun redactSensitiveProperties(properties: Map<String, Any>): Map<String, Any> {
        return properties.mapValues { (key, value) ->
            if (configService.sensitiveKeysBehavior.isSensitiveKey(key)) REDACTED_LABEL else value
        }
    }

    private fun sanitizeProperties(
        properties: Map<String, Any>,
        bypassPropertyLimit: Boolean = false,
    ): Map<String, Any> {
        return runCatching {
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
