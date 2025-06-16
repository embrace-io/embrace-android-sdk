package io.embrace.android.embracesdk.internal.utils

import android.os.Parcelable
import java.io.Serializable

/**
 * Utility to for sanitizing user-supplied properties.
 */
object PropertyUtils {

    const val MAX_PROPERTY_SIZE: Int = 10

    fun sanitizeProperties(properties: Map<String, Any>?, bypassPropertyLimit: Boolean = false): Map<String, Any> {
        return if (properties == null) {
            emptyMap()
        } else {
            runCatching {
                if (bypassPropertyLimit) {
                    properties.entries
                } else {
                    properties.entries.take(MAX_PROPERTY_SIZE)
                }.associate { Pair(it.key, checkIfSerializable(it.value)) }
            }.getOrDefault(emptyMap())
        }
    }

    private fun checkIfSerializable(value: Any): Any {
        if (!(value is Parcelable || value is Serializable)) {
            return "not serializable"
        }
        return value
    }
}
