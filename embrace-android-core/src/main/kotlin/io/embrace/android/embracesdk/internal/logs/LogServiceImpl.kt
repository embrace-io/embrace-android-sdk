package io.embrace.android.embracesdk.internal.logs

import android.os.Parcelable
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.utils.PropertyUtils.truncate
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import java.io.Serializable

/**
 * Creates log records to be sent using the Open Telemetry Logs data model.
 */
@OptIn(IncubatingApi::class)
class LogServiceImpl(
    private val destination: TelemetryDestination,
    private val configService: ConfigService,
    private val logLimitingService: LogLimitingService,
) : LogService {

    private val behavior = configService.logMessageBehavior
    private val bypassLimitsValidation = configService.isOnlyUsingOtelExporters()

    override fun log(
        message: String,
        severity: LogSeverity,
        attributes: Map<String, Any>,
        schemaProvider: (TelemetryAttributes) -> SchemaType,
    ) {
        if (!logLimitingService.addIfAllowed(severity)) {
            return
        }

        destination.addLog(
            schemaType = schemaProvider(
                TelemetryAttributes(
                    customAttributes = redactSensitiveAttributes(attributes)
                )
            ),
            severity = severity,
            message = trimToMaxLength(message)
        )
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
