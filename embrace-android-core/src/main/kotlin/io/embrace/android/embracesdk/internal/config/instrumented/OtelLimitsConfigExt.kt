package io.embrace.android.embracesdk.internal.config.instrumented

import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

internal fun OtelLimitsConfig.isNameValid(str: String, internal: Boolean): Boolean =
    str.isNotBlank() && ((internal && str.length <= getMaxInternalNameLength()) || str.length <= getMaxNameLength())

internal fun OtelLimitsConfig.isEventCountValid(events: List<EmbraceSpanEvent>, internal: Boolean): Boolean {
    val max = if (internal) {
        getMaxSystemEventCount()
    } else {
        getMaxCustomEventCount()
    }

    return events.size <= max
}

internal fun OtelLimitsConfig.isAttributeCountValid(attributes: Map<String, String>, internal: Boolean): Boolean {
    val max = if (internal) {
        getMaxSystemAttributeCount()
    } else {
        getMaxCustomAttributeCount()
    }

    return attributes.size <= max
}

internal fun OtelLimitsConfig.isAttributeValid(key: String, value: String, internal: Boolean) =
    ((internal && key.length <= getMaxInternalAttributeKeyLength()) || key.length <= getMaxCustomAttributeKeyLength()) &&
        ((internal && value.length <= getMaxInternalAttributeValueLength()) || value.length <= getMaxCustomAttributeValueLength())
