package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.limits.SPAN_EVENT_TELEMETRY_TYPE
import io.embrace.android.embracesdk.internal.limits.SpanLimits
import io.embrace.android.embracesdk.internal.limits.TelemetryLimitEnforcer
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

/**
 * Used to validate limits and restrictions at instrumentation time imposed by Embrace before telemetry is recorded
 */
class DataValidator(
    val otelLimitsConfig: OtelLimitsConfig = OtelLimitsConfigImpl,
    private val bypassValidation: (() -> Boolean) = { false },
    telemetryService: TelemetryService,
) {
    private val enforcer = TelemetryLimitEnforcer(
        otelLimits = otelLimitsConfig,
        telemetryService = telemetryService,
        exemptFromValueTruncation = String::isValidLongValueAttribute,
    )

    fun truncateName(name: String, internal: Boolean): String =
        enforcer.truncateName(name, SpanLimits.of(internal).maxNameLength)

    fun <T> truncateEvents(events: List<T>, internal: Boolean): List<T> = if (internal || !bypassValidation()) {
        enforcer.capCount(events, SpanLimits.of(internal).maxEventCount, SPAN_EVENT_TELEMETRY_TYPE)
    } else {
        events
    }

    fun truncateAttributes(attributes: Map<String, String>, internal: Boolean, countOverride: Int? = null): Map<String, String> {
        if (!internal && bypassValidation()) {
            return attributes
        }
        val limits = SpanLimits.of(internal)
        return enforcer.truncateAttributes(
            attributes = attributes,
            maxCount = countOverride ?: limits.maxAttributeCount,
            maxKeyLength = limits.maxAttributeKeyLength,
            maxValueLength = limits.maxAttributeValueLength,
        )
    }

    fun truncateAttribute(key: String, value: String, internal: Boolean): Pair<String, String> {
        val limits = SpanLimits.of(internal)
        return enforcer.truncateAttribute(
            key = key,
            value = value,
            maxKeyLength = limits.maxAttributeKeyLength,
            maxValueLength = limits.maxAttributeValueLength,
        )
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
                countOverride = otelLimitsConfig.getMaxEventAttributeCount(),
            ),
        )
    }
}
