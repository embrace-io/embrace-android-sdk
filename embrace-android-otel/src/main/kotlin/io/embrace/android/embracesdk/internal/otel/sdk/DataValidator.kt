package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.arch.datasource.SpanEventImpl
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.limits.SPAN_EVENT_TELEMETRY_TYPE
import io.embrace.android.embracesdk.internal.limits.SpanLimits
import io.embrace.android.embracesdk.internal.limits.TelemetryLimitEnforcer
import io.embrace.android.embracesdk.internal.otel.payload.toPayloadString
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService

/**
 * Used to validate limits and restrictions at instrumentation time imposed by Embrace before telemetry is recorded
 */
class DataValidator(
    val otelLimitsConfig: OtelLimitsConfig = OtelLimitsConfigImpl,
    private val bypassValidation: (() -> Boolean) = { false },
    telemetryService: InternalTelemetryService,
) {
    private val enforcer = TelemetryLimitEnforcer(
        otelLimits = otelLimitsConfig,
        telemetryService = telemetryService,
        exemptFromValueTruncation = String::isValidLongValueAttribute,
        valueToString = { it.toPayloadString().orEmpty() },
    )

    fun truncateName(name: String, internal: Boolean): String =
        enforcer.truncateName(name, SpanLimits.of(internal).maxNameLength)

    fun <T> truncateEvents(events: List<T>, internal: Boolean): List<T> = if (internal || !bypassValidation()) {
        enforcer.capCount(events, SpanLimits.of(internal).maxEventCount, SPAN_EVENT_TELEMETRY_TYPE)
    } else {
        events
    }

    fun truncateAttributes(attributes: Map<String, Any>, internal: Boolean, countOverride: Int? = null): Map<String, Any> {
        if (!internal && bypassValidation()) {
            // return a copy so future mutations doesn't affect what is returned
            return attributes.toMap()
        }
        val limits = SpanLimits.of(internal)
        return enforcer.truncateAttributes(
            attributes = attributes,
            maxCount = countOverride ?: limits.maxAttributeCount,
            maxKeyLength = limits.maxAttributeKeyLength,
            maxValueLength = limits.maxAttributeValueLength,
        )
    }

    fun truncateAttribute(key: String, value: Any, internal: Boolean): Pair<String, Any> {
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
        attributes: Map<String, Any>,
    ): SpanEventImpl {
        return SpanEventImpl(
            name = truncateName(name, internal),
            timestampNanos = timestampMs.millisToNanos(),
            attributes = truncateAttributes(
                attributes = attributes,
                internal = internal,
                countOverride = otelLimitsConfig.getMaxEventAttributeCount(),
            ),
        )
    }
}
