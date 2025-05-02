package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.capture.session.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.attrs.asOtelAttributeKey
import io.embrace.android.embracesdk.internal.utils.isBlankish
import io.opentelemetry.api.common.AttributeKey

/**
 * Object that aggregates various attributes and returns a [Map] that represents the values at the current state
 */
class TelemetryAttributes(
    private val configService: ConfigService,
    private val sessionPropertiesProvider: () -> Map<String, String>? = { null },
    private val customAttributes: Map<String, String>? = null,
) {
    private val map: MutableMap<AttributeKey<String>, String> = mutableMapOf()

    /**
     * Return a snapshot of the current values of the attributes set on this as a [Map]. Schema keys will always overwrite any previous
     * entries set on the object.
     */
    fun snapshot(): Map<String, String> {
        val shouldGateLogProperties = configService.sessionBehavior.shouldGateLogProperties()
        val shouldGateSessionProperties = configService.sessionBehavior.shouldGateSessionProperties()
        val result = mutableMapOf<String, String>()

        if (!shouldGateLogProperties) {
            customAttributes?.let { result.putAll(it) }
        }

        if (!shouldGateSessionProperties) {
            sessionPropertiesProvider()?.let { properties ->
                result.putAll(properties.mapKeys { property -> property.key.toSessionPropertyAttributeName() })
            }
        }

        result.putAll(map.mapKeys { it.key.key })

        return result
    }

    fun setAttribute(key: EmbraceAttributeKey, value: String, keepBlankishValues: Boolean = true) {
        setAttribute(key.asOtelAttributeKey(), value, keepBlankishValues)
    }

    fun setAttribute(key: AttributeKey<String>, value: String, keepBlankishValues: Boolean = true) {
        if (keepBlankishValues || !value.isBlankish()) {
            map[key] = value
        }
    }

    fun getAttribute(key: EmbraceAttributeKey): String? = map[key.asOtelAttributeKey()]

    fun getAttribute(key: AttributeKey<String>): String? = map[key]
}
