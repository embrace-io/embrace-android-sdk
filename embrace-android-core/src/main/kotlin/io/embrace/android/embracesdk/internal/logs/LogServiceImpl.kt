package io.embrace.android.embracesdk.internal.logs

import android.os.Parcelable
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.LimitedTelemetryType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
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
    private val telemetryService: TelemetryService,
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
        attributes: Map<String, Any>,
        schemaProvider: (TelemetryAttributes) -> SchemaType,
    ) {
        val telemetryType = when (severity) {
            LogSeverity.ERROR -> LimitedTelemetryType.ERROR_LOG
            LogSeverity.WARNING -> LimitedTelemetryType.WARNING_LOG
            LogSeverity.INFO -> LimitedTelemetryType.INFO_LOG
        }

        if (!logCounters.getValue(severity).addIfAllowed()) {
            telemetryService.trackAppliedLimit(telemetryType, AppliedLimitType.DROP)
            return
        }

        val redactedAttributes = redactSensitiveAttributes(attributes, telemetryType)
        val telemetryAttributes = TelemetryAttributes(
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = redactedAttributes.plus(LogAttributes.LOG_RECORD_UID to Uuid.getEmbUuid()),
        )

        destination.addLog(schemaProvider(telemetryAttributes), severity, trimToMaxLength(message, telemetryType))
    }

    override fun getErrorLogsCount(): Int {
        return logCounters.getValue(LogSeverity.ERROR).getCount()
    }

    override fun onPostSessionChange() {
        logCounters.forEach { it.value.clear() }
    }

    private fun trimToMaxLength(message: String, telemetryType: LimitedTelemetryType): String {
        val maxLength = if (configService.appFramework == AppFramework.UNITY) {
            LOG_MESSAGE_UNITY_MAXIMUM_ALLOWED_LENGTH
        } else {
            behavior.getLogMessageMaximumAllowedLength()
        }

        if (message.length > maxLength) {
            telemetryService.trackAppliedLimit(telemetryType, AppliedLimitType.TRUNCATE_STRING)

            val endChars = "..."

            if (maxLength <= endChars.length) {
                return message.take(maxLength)
            }
            return message.take(maxLength - endChars.length) + endChars
        } else {
            return message
        }
    }

    private fun redactSensitiveAttributes(attributes: Map<String, Any>, telemetryType: LimitedTelemetryType): Map<String, String> {
        return sanitizeAttributes(
            attributes = attributes,
            telemetryType = telemetryType,
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
        telemetryType: LimitedTelemetryType,
        bypassPropertyLimit: Boolean = false,
    ): Map<String, String> {
        return runCatching {
            if (bypassPropertyLimit) {
                return@runCatching attributes.mapValues { checkIfSerializable(it.value).toString() }
            }

            if (attributes.size > MAX_PROPERTY_COUNT) {
                telemetryService.trackAppliedLimit(telemetryType, AppliedLimitType.TRUNCATE_ATTRIBUTES)
            }

            // Process entries
            attributes.entries.take(MAX_PROPERTY_COUNT).associate { (key, value) ->
                val truncatedKey =
                    truncateAndTrack(key, MAX_PROPERTY_KEY_LENGTH, LimitedTelemetryType.LOG_ATTRIBUTE_KEY)

                val stringValue = checkIfSerializable(value).toString()
                val truncatedValue = if (key == ExceptionAttributes.EXCEPTION_STACKTRACE) {
                    stringValue
                } else {
                    truncateAndTrack(stringValue, MAX_PROPERTY_VALUE_LENGTH, LimitedTelemetryType.LOG_ATTRIBUTE_VALUE)
                }

                truncatedKey to truncatedValue
            }
        }.getOrDefault(emptyMap())
    }

    private fun checkIfSerializable(value: Any): Any {
        if (!(value is Parcelable || value is Serializable)) {
            return "not serializable"
        }
        return value
    }

    private fun truncateAndTrack(
        value: String,
        limit: Int,
        telemetryType: LimitedTelemetryType
    ): String {
        val truncated = truncate(value, limit)
        if (truncated != value) {
            telemetryService.trackAppliedLimit(telemetryType, AppliedLimitType.TRUNCATE_STRING)
        }
        return truncated
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
