package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.opentelemetry.api.common.AttributeKey

/**
 * Object that aggregates various attributes and returns a [Map] that represents the values at the current state
 */
public class TelemetryAttributes(
    private val configService: ConfigService,
    private val sessionPropertiesProvider: () -> Map<String, String>? = { null },
    private val customAttributes: Map<String, String>? = null
) {
    private val map: MutableMap<AttributeKey<String>, String> = mutableMapOf()

    /**
     * Return a snapshot of the current values of the attributes set on this as a [Map]. Schema keys will always overwrite any previous
     * entries set on the object.
     */
    public fun snapshot(): Map<String, String> {
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

    public fun setAttribute(key: EmbraceAttributeKey, value: String) {
        setAttribute(key.attributeKey, value)
    }

    public fun setAttribute(key: AttributeKey<String>, value: String) {
        map[key] = value
    }

    public fun getAttribute(key: EmbraceAttributeKey): String? = map[key.attributeKey]

    public fun getAttribute(key: AttributeKey<String>): String? = map[key]
}
