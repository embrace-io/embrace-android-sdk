package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.utils.PropertyUtils
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

/**
 * Used to validate limits and restrictions at instrumentation time imposed by Embrace before telemetry is recorded
 */
class DataValidator(
    val otelLimitsConfig: OtelLimitsConfig = OtelLimitsConfigImpl,
    private val bypassValidation: (() -> Boolean) = { false },
) {
    fun truncateName(name: String, internal: Boolean): String {
        val maxLength = if (internal) {
            otelLimitsConfig.getMaxInternalNameLength()
        } else {
            otelLimitsConfig.getMaxNameLength()
        }
        return PropertyUtils.truncate(name, maxLength)
    }

    fun truncateEvents(events: List<EmbraceSpanEvent>, internal: Boolean): List<EmbraceSpanEvent> {
        return if (internal) {
            events.take(otelLimitsConfig.getMaxSystemEventCount())
        } else if (!bypassValidation()) {
            events.take(otelLimitsConfig.getMaxCustomEventCount())
        } else {
            events
        }
    }

    fun truncateAttributes(attributes: Map<String, String>, internal: Boolean, countOverride: Int? = null): Map<String, String> {
        val maxAttributeCount = countOverride ?: if (internal) {
            otelLimitsConfig.getMaxSystemAttributeCount()
        } else {
            otelLimitsConfig.getMaxCustomAttributeCount()
        }
        val maxKeyLength = if (internal) {
            otelLimitsConfig.getMaxInternalAttributeKeyLength()
        } else {
            otelLimitsConfig.getMaxCustomAttributeKeyLength()
        }
        val maxValueLength = if (internal) {
            otelLimitsConfig.getMaxInternalAttributeValueLength()
        } else {
            otelLimitsConfig.getMaxCustomAttributeValueLength()
        }

        return if (internal || !bypassValidation()) {
            attributes.truncate(
                maxCount = maxAttributeCount,
                maxKeyLength = maxKeyLength,
                maxValueLength = maxValueLength
            )
        } else {
            attributes
        }
    }

    fun truncateAttribute(key: String, value: String, internal: Boolean): Pair<String, String> {
        val maxKeyLength = if (internal) {
            otelLimitsConfig.getMaxInternalAttributeKeyLength()
        } else {
            otelLimitsConfig.getMaxCustomAttributeKeyLength()
        }
        val maxValueLength = if (internal) {
            otelLimitsConfig.getMaxInternalAttributeValueLength()
        } else {
            otelLimitsConfig.getMaxCustomAttributeValueLength()
        }

        val truncatedValue = if (key.isValidLongValueAttribute()) {
            value
        } else {
            PropertyUtils.truncate(value, maxValueLength)
        }

        return Pair(PropertyUtils.truncate(key, maxKeyLength), truncatedValue)
    }

    fun createTruncatedSpanEvent(
        name: String,
        timestampMs: Long,
        internal: Boolean,
        attributes: Map<String, String>,
    ): EmbraceSpanEvent? {
        return EmbraceSpanEvent.create(
            name = truncateName(name, internal),
            timestampMs = timestampMs,
            attributes = truncateAttributes(
                attributes = attributes,
                internal = internal,
                countOverride = otelLimitsConfig.getMaxEventAttributeCount()
            )
        )
    }

    private fun Map<String, String>.truncate(
        maxCount: Int,
        maxKeyLength: Int,
        maxValueLength: Int,
    ): Map<String, String> =
        entries.take(maxCount).associate {
            PropertyUtils.truncate(
                value = it.key,
                maxLength = maxKeyLength
            ) to truncateAttributeValue(
                key = it.key,
                value = it.value,
                maxLength = maxValueLength
            )
        }

    private fun truncateAttributeValue(key: String, value: String, maxLength: Int): String =
        if (key.isValidLongValueAttribute()) {
            value
        } else {
            PropertyUtils.truncate(value, maxLength)
        }
}
