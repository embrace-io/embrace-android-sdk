package io.embrace.android.embracesdk.internal.limits

import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl

/**
 * The limits that apply to a span, its events, its links and its attributes.
 */
sealed class SpanLimits(val internal: Boolean) {

    abstract val maxNameLength: Int
    abstract val maxEventCount: Int
    abstract val maxLinkCount: Int
    abstract val maxAttributeCount: Int
    abstract val maxAttributeKeyLength: Int
    abstract val maxAttributeValueLength: Int

    /**
     * Limits for telemetry the SDK records itself, which the app has no direct control over.
     */
    object Internal : SpanLimits(internal = true) {
        override val maxNameLength: Int = OtelLimitsConfigImpl.getMaxInternalNameLength()
        override val maxEventCount: Int = OtelLimitsConfigImpl.getMaxSystemEventCount()
        override val maxLinkCount: Int = OtelLimitsConfigImpl.getMaxSystemLinkCount()
        override val maxAttributeCount: Int = OtelLimitsConfigImpl.getMaxSystemAttributeCount()
        override val maxAttributeKeyLength: Int = OtelLimitsConfigImpl.getMaxInternalAttributeKeyLength()
        override val maxAttributeValueLength: Int = OtelLimitsConfigImpl.getMaxInternalAttributeValueLength()
    }

    /**
     * Limits for telemetry recorded through the public API, where a misbehaving app could otherwise
     * exhaust memory or produce payloads the backend will reject.
     */
    object Custom : SpanLimits(internal = false) {
        override val maxNameLength: Int = OtelLimitsConfigImpl.getMaxNameLength()
        override val maxEventCount: Int = OtelLimitsConfigImpl.getMaxCustomEventCount()
        override val maxLinkCount: Int = OtelLimitsConfigImpl.getMaxCustomLinkCount()
        override val maxAttributeCount: Int = OtelLimitsConfigImpl.getMaxCustomAttributeCount()
        override val maxAttributeKeyLength: Int = OtelLimitsConfigImpl.getMaxCustomAttributeKeyLength()
        override val maxAttributeValueLength: Int = OtelLimitsConfigImpl.getMaxCustomAttributeValueLength()
    }

    companion object {
        fun of(internal: Boolean): SpanLimits = if (internal) Internal else Custom
    }
}
