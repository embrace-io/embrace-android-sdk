package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

/**
 * Used to validate limits and restrictions at instrumentation time imposed by Embrace before telemetry is recorded
 */
class DataValidator(
    val otelLimitsConfig: OtelLimitsConfig = OtelLimitsConfigImpl,
    private val bypassValidation: (() -> Boolean) = { false },
) {
    fun isNameValid(str: String, internal: Boolean): Boolean {
        return if (internal) {
            str.isNotBlank() && str.length <= otelLimitsConfig.getMaxInternalNameLength()
        } else if (!bypassValidation()) {
            str.isNotBlank() && str.length <= otelLimitsConfig.getMaxNameLength()
        } else {
            true
        }
    }

    fun isEventCountValid(events: List<EmbraceSpanEvent>, internal: Boolean): Boolean {
        return if (internal) {
            events.size <= otelLimitsConfig.getMaxSystemEventCount()
        } else if (!bypassValidation()) {
            events.size <= otelLimitsConfig.getMaxCustomEventCount()
        } else {
            true
        }
    }

    fun isAttributeCountValid(attributes: Map<String, String>, internal: Boolean): Boolean {
        return if (internal) {
            attributes.size <= otelLimitsConfig.getMaxSystemAttributeCount()
        } else if (!bypassValidation()) {
            attributes.size <= otelLimitsConfig.getMaxCustomAttributeCount()
        } else {
            true
        }
    }

    fun isAttributeValid(key: String, value: String, internal: Boolean): Boolean {
        with(otelLimitsConfig) {
            return if (internal) {
                key.length <= getMaxInternalAttributeKeyLength() && value.length <= getMaxInternalAttributeValueLength()
            } else if (!bypassValidation()) {
                key.length <= getMaxCustomAttributeKeyLength() && value.length <= getMaxCustomAttributeValueLength()
            } else {
                true
            }
        }
    }
}
