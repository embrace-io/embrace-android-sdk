package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.utils.isBlankish

/**
 * Object that aggregates various attributes and returns a [Map] that represents the values at the current state
 */
class TelemetryAttributes(
    private val configService: ConfigService,
    private val sessionPropertiesProvider: () -> Map<String, String>? = { null },
    private val customAttributes: Map<String, String>? = null,
) {
    private val map: MutableMap<String, String> = mutableMapOf()

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

        result.putAll(map.mapKeys { it.key })

        return result
    }

    fun setAttribute(key: EmbraceAttributeKey, value: String, keepBlankishValues: Boolean = true) {
        setAttribute(key.attributeKey, value, keepBlankishValues)
    }

    fun setAttribute(key: String, value: String, keepBlankishValues: Boolean = true) {
        if (keepBlankishValues || !value.isBlankish()) {
            map[key] = value
        }
    }

    fun getAttribute(key: EmbraceAttributeKey): String? = map[key.attributeKey]

    fun getAttribute(key: String): String? = map[key]
}
