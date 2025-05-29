package io.embrace.android.embracesdk.internal.utils

import android.os.Parcelable
import java.io.Serializable

/**
 * Utility to for sanitizing user-supplied properties.
 */
object PropertyUtils {
    /**
     * This method will normalize the map by applying the following rules:
     *
     * - Null key registries will be discarded.
     * - Null value registries will be renamed to null as a String.
     *
     * @param properties properties to be normalized.
     * @return a normalized Map of the provided properties.
     */
    @JvmStatic
    fun sanitizeProperties(properties: Map<String, Any?>?): Map<String, Any> {
        properties ?: return emptyMap()

        return properties.entries
            .associate { Pair(it.key, checkIfSerializable(it.value)) }
    }

    @JvmStatic
    fun normalizeProperties(properties: Map<String, Any>?): Map<String, Any>? {
        var normalizedProperties: Map<String, Any> = HashMap()
        if (properties != null) {
            runCatching {
                normalizedProperties = sanitizeProperties(properties)
            }
            return normalizedProperties
        } else {
            return null
        }
    }

    private fun checkIfSerializable(value: Any?): Any {
        if (value == null) {
            return "null"
        }
        if (!(value is Parcelable || value is Serializable)) {
            return "not serializable"
        }
        return value
    }
}
