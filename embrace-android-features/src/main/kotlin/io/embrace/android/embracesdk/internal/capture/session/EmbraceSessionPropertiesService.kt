package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.internal.utils.Provider

public class EmbraceSessionPropertiesService(
    private val listener: (Map<String, String>) -> Unit,
    private val sessionProperties: EmbraceSessionProperties,
    private val dataSourceProvider: Provider<SessionPropertiesDataSource?>
) : SessionPropertiesService {

    override fun addProperty(originalKey: String, originalValue: String, permanent: Boolean): Boolean {
        if (!isValidKey(originalKey)) {
            return false
        }
        val sanitizedKey = enforceLength(originalKey, SESSION_PROPERTY_KEY_LIMIT)

        if (!isValidValue(originalValue)) {
            return false
        }
        val sanitizedValue = enforceLength(originalValue, SESSION_PROPERTY_VALUE_LIMIT)

        val added = sessionProperties.add(sanitizedKey, sanitizedValue, permanent)
        if (added) {
            dataSourceProvider()?.apply {
                addProperty(sanitizedKey, sanitizedValue)
            }
            listener(sessionProperties.get())
        }
        return added
    }

    override fun removeProperty(originalKey: String): Boolean {
        if (!isValidKey(originalKey)) {
            return false
        }
        val sanitizedKey = enforceLength(originalKey, SESSION_PROPERTY_KEY_LIMIT)

        val removed = sessionProperties.remove(sanitizedKey)
        if (removed) {
            dataSourceProvider()?.apply {
                removeProperty(sanitizedKey)
            }
            listener(sessionProperties.get())
        }
        return removed
    }

    override fun getProperties(): Map<String, String> = sessionProperties.get()

    override fun populateCurrentSession(): Boolean = dataSourceProvider()?.addProperties(getProperties()) ?: false

    private fun isValidKey(key: String?): Boolean = !key.isNullOrEmpty()

    private fun isValidValue(key: String?): Boolean = key != null

    private fun enforceLength(value: String, maxLength: Int): String {
        if (value.length <= maxLength) {
            return value
        }
        val endChars = "..."
        return value.substring(0, maxLength - endChars.length) + endChars
    }

    private companion object {
        /**
         * The maximum number of characters of a session property key
         */
        private const val SESSION_PROPERTY_KEY_LIMIT = 128

        /**
         * The maximum number of characters of a session property value
         */
        private const val SESSION_PROPERTY_VALUE_LIMIT = 1024
    }
}
