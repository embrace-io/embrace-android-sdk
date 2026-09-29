package io.embrace.android.embracesdk.internal.limits

import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.PropertyUtils

/**
 * Applies the upper bounds of captured telemetry, reporting every limit that
 * had to be applied via [TelemetryService.trackAppliedLimit].
 */
class TelemetryLimitEnforcer(
    val otelLimits: OtelLimitsConfig = OtelLimitsConfigImpl,
    private val telemetryService: TelemetryService,
    private val exemptFromValueTruncation: (String) -> Boolean = { false },
    private val valueToString: (Any) -> String = Any::toString,
) {

    fun truncateName(name: String, maxLength: Int): String {
        val truncated = PropertyUtils.truncate(name, maxLength)
        if (truncated != name) {
            telemetryService.trackAppliedLimit(SPAN_NAME_TELEMETRY_TYPE, AppliedLimitType.TRUNCATE_STRING)
        }
        return truncated
    }

    fun truncateAttributes(
        attributes: Map<String, Any>,
        maxCount: Int,
        maxKeyLength: Int,
        maxValueLength: Int,
    ): Map<String, Any> {
        val truncatedEntries = attributes.entries.take(maxCount)
        if (truncatedEntries.size < attributes.size) {
            telemetryService.trackAppliedLimit(SPAN_ATTRIBUTE_TELEMETRY_TYPE, AppliedLimitType.TRUNCATE_ATTRIBUTES)
        }
        return truncatedEntries.associate {
            truncateAttribute(
                key = it.key,
                value = it.value,
                maxKeyLength = maxKeyLength,
                maxValueLength = maxValueLength,
            )
        }
    }

    fun truncateAttribute(key: String, value: Any, maxKeyLength: Int, maxValueLength: Int): Pair<String, Any> {
        val truncatedKey = PropertyUtils.truncate(key, maxKeyLength)
        if (truncatedKey != key) {
            telemetryService.trackAppliedLimit(SPAN_ATTRIBUTE_KEY_TELEMETRY_TYPE, AppliedLimitType.TRUNCATE_STRING)
        }

        val truncatedValue = if (exemptFromValueTruncation(key)) {
            value
        } else {
            truncateValue(value, maxValueLength)
        }
        if (truncatedValue != value) {
            telemetryService.trackAppliedLimit(SPAN_ATTRIBUTE_VALUE_TELEMETRY_TYPE, AppliedLimitType.TRUNCATE_STRING)
        }

        return Pair(truncatedKey, truncatedValue)
    }

    private fun truncateValue(value: Any, maxLength: Int): Any {
        if (value is String) {
            return PropertyUtils.truncate(value, maxLength)
        }
        val stringValue = valueToString(value)
        return if (stringValue.length > maxLength) PropertyUtils.truncate(stringValue, maxLength) else value
    }

    /**
     * Drops everything past the first [max] items, reporting the drop against [telemetryType].
     */
    fun <T> capCount(items: List<T>, max: Int, telemetryType: String): List<T> {
        val capped = items.take(max)
        if (capped.size < items.size) {
            telemetryService.trackAppliedLimit(telemetryType, AppliedLimitType.DROP)
        }
        return capped
    }
}

const val SPAN_EVENT_TELEMETRY_TYPE = "span_event"

private const val SPAN_NAME_TELEMETRY_TYPE = "span_name"
private const val SPAN_ATTRIBUTE_TELEMETRY_TYPE = "span_attribute"
private const val SPAN_ATTRIBUTE_KEY_TELEMETRY_TYPE = "span_attribute_key"
private const val SPAN_ATTRIBUTE_VALUE_TELEMETRY_TYPE = "span_attribute_value"
