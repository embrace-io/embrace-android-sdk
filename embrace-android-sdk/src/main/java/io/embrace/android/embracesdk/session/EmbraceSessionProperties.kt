package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService

internal class EmbraceSessionProperties(
    private val preferencesService: PreferencesService,
    private val logger: InternalEmbraceLogger,
    private val configService: ConfigService
) {
    private val temporary: MutableMap<String, String> = HashMap()
    private var permanent: MutableMap<String, String>

    init {
        // TODO: this blocks on the preferences being successfully read from this. Are we cool with this?
        val existingPermanent: Map<String, String>? = preferencesService.permanentSessionProperties
        permanent = existingPermanent?.let(::HashMap) ?: HashMap()
    }

    private fun haveKey(key: String): Boolean {
        return permanent.containsKey(key) || temporary.containsKey(key)
    }

    private fun isValidKey(key: String?): Boolean {
        if (key == null) {
            logger.logError("Session property key cannot be null")
            return false
        }
        if (key == "") {
            logger.logError("Session property key cannot be empty string")
            return false
        }
        return true
    }

    private fun isValidValue(key: String?): Boolean {
        if (key == null) {
            logger.logError("Session property value cannot be null")
            return false
        }
        return true
    }

    private fun enforceLength(value: String, maxLength: Int): String {
        if (value.length <= maxLength) {
            return value
        }
        val endChars = "..."
        return value.substring(0, maxLength - endChars.length) + endChars
    }

    @Synchronized
    fun add(key: String, value: String, isPermanent: Boolean): Boolean {
        if (!isValidKey(key)) {
            return false
        }
        val sanitizedKey = enforceLength(key, SESSION_PROPERTY_KEY_LIMIT)
        if (!isValidValue(value)) {
            return false
        }
        val sanitizedValue = enforceLength(value, SESSION_PROPERTY_VALUE_LIMIT)
        val maxSessionProperties = configService.sessionBehavior.getMaxSessionProperties()
        if (size() > maxSessionProperties || size() == maxSessionProperties && !haveKey(sanitizedKey)) {
            logger.logError("Session property count is at its limit. Rejecting.")
            return false
        }

        // add to selected destination, deleting the key if it exists in the other destination
        if (isPermanent) {
            permanent[sanitizedKey] = sanitizedValue
            temporary.remove(sanitizedKey)
            preferencesService.permanentSessionProperties = permanent
        } else {
            // only save the permanent values if the key existed in the permanent map
            if (permanent.remove(sanitizedKey) != null) {
                preferencesService.permanentSessionProperties = permanent
            }
            temporary[sanitizedKey] = sanitizedValue
        }
        return true
    }

    @Synchronized
    fun remove(key: String): Boolean {
        if (!isValidKey(key)) {
            return false
        }
        val sanitizedKey = enforceLength(key, SESSION_PROPERTY_KEY_LIMIT)
        var existed = false
        if (temporary.remove(sanitizedKey) != null) {
            existed = true
        }
        if (permanent.remove(sanitizedKey) != null) {
            preferencesService.permanentSessionProperties = permanent
            existed = true
        }
        return existed
    }

    @Synchronized
    fun get(): Map<String, String> = permanent.plus(temporary)

    private fun size(): Int = permanent.size + temporary.size

    fun clearTemporary() = temporary.clear()

    companion object {

        /**
         * The maximum number of properties that can be attached to a session
         */
        private const val SESSION_PROPERTY_KEY_LIMIT = 128
        private const val SESSION_PROPERTY_VALUE_LIMIT = 1024
    }
}
