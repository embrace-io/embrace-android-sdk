package io.embrace.android.embracesdk.utils

import android.os.Parcelable
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logWarning
import java.io.Serializable

/**
 * Utility to for sanitizing user-supplied properties.
 */
internal object PropertyUtils {

    const val MAX_PROPERTY_SIZE = 10

    /**
     * This method will normalize the map by applying the following rules:
     *
     * - Null key registries will be discarded.
     * - Null value registries will be renamed to null as a String.
     * - Cap the properties map to a maximum of [PropertyUtils.MAX_PROPERTY_SIZE] properties.
     *
     * @param properties properties to be normalized.
     * @return a normalized Map of the provided properties.
     */
    @JvmStatic
    fun sanitizeProperties(properties: Map<String?, Any?>?): Map<String, Any> {
        properties ?: return emptyMap()

        if (properties.size > MAX_PROPERTY_SIZE) {
            logWarning("The maximum number of properties is $MAX_PROPERTY_SIZE, the rest will be ignored.")
        }
        return properties.entries
            .filter { it.key != null }
            .take(MAX_PROPERTY_SIZE)
            .associate { Pair(it.key ?: "null", checkIfSerializable(it.key ?: "", it.value)) }
    }

    private fun checkIfSerializable(key: String, value: Any?): Any {
        if (value == null) {
            return "null"
        }
        if (!(value is Parcelable || value is Serializable)) {
            val msg = "The property with key $key has an entry that cannot be serialized. It will be ignored."
            logWarning(msg)
            return "not serializable"
        }
        return value
    }
}
