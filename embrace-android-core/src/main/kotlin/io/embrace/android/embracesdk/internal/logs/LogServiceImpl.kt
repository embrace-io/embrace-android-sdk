package io.embrace.android.embracesdk.internal.logs

import android.os.Parcelable
import io.embrace.android.embracesdk.LogExceptionType
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
import io.embrace.android.embracesdk.internal.payload.AppFramework
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
class LogServiceImpl(
    private val destination: TelemetryDestination,
    private val configService: ConfigService,
    private val sessionPropertiesService: SessionPropertiesService,
) : LogService {

    private val behavior = configService.logMessageBehavior
    private val bypassLimitsValidation = configService.isOnlyUsingOtelExporters()
    private val logCounters = mapOf(
        LogSeverity.INFO to LogCounter(behavior::getInfoLogLimit),
        LogSeverity.WARNING to LogCounter(behavior::getWarnLogLimit),
        LogSeverity.ERROR to LogCounter(behavior::getErrorLogLimit)
    )

    override fun log(
        message: String,
        severity: LogSeverity,
        logExceptionType: LogExceptionType,
        attributes: Map<String, Any>,
    ) {
        if (!logCounters.getValue(severity).addIfAllowed()) {
            return
        }

        val redactedAttributes = redactSensitiveAttributes(attributes)
        val telemetryAttributes = TelemetryAttributes(
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = redactedAttributes.plus(LogAttributes.LOG_RECORD_UID to Uuid.getEmbUuid()),
        )

        val schemaProvider: (TelemetryAttributes) -> SchemaType = when {
            logExceptionType == LogExceptionType.NONE -> ::Log
            configService.appFramework == AppFramework.FLUTTER -> ::FlutterException
            else -> ::Exception
        }

        destination.addLog(schemaProvider(telemetryAttributes), severity, trimToMaxLength(message))
    }

    override fun getErrorLogsCount(): Int {
        return logCounters.getValue(LogSeverity.ERROR).getCount()
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

    private fun redactSensitiveAttributes(attributes: Map<String, Any>): Map<String, String> {
        return sanitizeAttributes(
            attributes = attributes,
            bypassPropertyLimit = bypassLimitsValidation
        ).mapValues { (key, value) ->
            when {
                configService.sensitiveKeysBehavior.isSensitiveKey(key) -> REDACTED_LABEL
                else -> value
            }
        }
    }

    private fun sanitizeAttributes(
        attributes: Map<String, Any>,
        bypassPropertyLimit: Boolean = false,
    ): Map<String, String> {
        return runCatching {
            if (bypassPropertyLimit) {
                attributes.entries.associate {
                    Pair(it.key, checkIfSerializable(it.value).toString())
                }
            } else {
                attributes.entries.take(MAX_PROPERTY_COUNT).associate {
                    Pair(
                        first = truncate(it.key, MAX_PROPERTY_KEY_LENGTH),
                        second = if (it.key == ExceptionAttributes.EXCEPTION_STACKTRACE) {
                            it.value.toString()
                        } else {
                            truncate(checkIfSerializable(it.value).toString(), MAX_PROPERTY_VALUE_LENGTH)
                        }
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
