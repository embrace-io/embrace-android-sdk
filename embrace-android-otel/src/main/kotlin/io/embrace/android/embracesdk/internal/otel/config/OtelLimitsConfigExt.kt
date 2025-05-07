package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

fun OtelLimitsConfig.isNameValid(str: String, internal: Boolean): Boolean =
    str.isNotBlank() && ((internal && str.length <= getMaxInternalNameLength()) || str.length <= getMaxNameLength())

fun OtelLimitsConfig.isEventCountValid(events: List<EmbraceSpanEvent>, internal: Boolean): Boolean {
    val max = if (internal) {
        getMaxSystemEventCount()
    } else {
        getMaxCustomEventCount()
    }

    return events.size <= max
}

fun OtelLimitsConfig.isAttributeCountValid(attributes: Map<String, String>, internal: Boolean): Boolean {
    val max = if (internal) {
        getMaxSystemAttributeCount()
    } else {
        getMaxCustomAttributeCount()
    }

    return attributes.size <= max
}

fun OtelLimitsConfig.isAttributeValid(key: String, value: String, internal: Boolean) =
    ((internal && key.length <= getMaxInternalAttributeKeyLength()) || key.length <= getMaxCustomAttributeKeyLength()) &&
        ((internal && value.length <= getMaxInternalAttributeValueLength()) || value.length <= getMaxCustomAttributeValueLength())

fun OtelLimitsConfig.getMaxTotalAttributeCount() = getMaxSystemAttributeCount() + getMaxCustomAttributeCount()

fun OtelLimitsConfig.getMaxTotalEventCount() = getMaxSystemEventCount() + getMaxCustomEventCount()

fun OtelLimitsConfig.getMaxTotalLinkCount() = getMaxSystemLinkCount() + getMaxCustomLinkCount()
