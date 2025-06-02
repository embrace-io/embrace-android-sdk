package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

class LimitsValidator(
    val otelLimitsConfig: OtelLimitsConfig = OtelLimitsConfigImpl,
    private val bypassLimitsValidation: (() -> Boolean) = { false },
) {
    fun isNameValid(str: String, internal: Boolean): Boolean {
        return if (internal) {
            str.isNotBlank() && str.length <= otelLimitsConfig.getMaxInternalNameLength()
        } else if (!bypassLimitsValidation()) {
            str.isNotBlank() && str.length <= otelLimitsConfig.getMaxNameLength()
        } else {
            true
        }
    }

    fun isEventCountValid(events: List<EmbraceSpanEvent>, internal: Boolean): Boolean {
        return if (internal) {
            events.size <= otelLimitsConfig.getMaxSystemEventCount()
        } else if (!bypassLimitsValidation()) {
            events.size <= otelLimitsConfig.getMaxCustomEventCount()
        } else {
            true
        }
    }

    fun isAttributeCountValid(attributes: Map<String, String>, internal: Boolean): Boolean {
        return if (internal) {
            attributes.size <= otelLimitsConfig.getMaxSystemAttributeCount()
        } else if (!bypassLimitsValidation()) {
            attributes.size <= otelLimitsConfig.getMaxCustomAttributeCount()
        } else {
            true
        }
    }

    fun isAttributeValid(key: String, value: String, internal: Boolean): Boolean {
        with(otelLimitsConfig) {
            return if (internal) {
                key.length <= getMaxInternalAttributeKeyLength() && value.length <= getMaxInternalAttributeValueLength()
            } else if (!bypassLimitsValidation()) {
                key.length <= getMaxCustomAttributeKeyLength() && value.length <= getMaxCustomAttributeValueLength()
            } else {
                true
            }
        }
    }
}
