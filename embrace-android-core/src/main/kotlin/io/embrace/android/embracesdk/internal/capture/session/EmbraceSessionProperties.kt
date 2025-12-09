package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.concurrent.atomic.AtomicReference

internal class EmbraceSessionProperties(
    private val store: KeyValueStore,
    private val configService: ConfigService,
    private val destination: TelemetryDestination,
) {
    private val temporary: MutableMap<String, String> = HashMap()
    private val permanentPropertiesReference = AtomicReference(NOT_LOADED)
    private val permanentPropertiesProvider: Provider<MutableMap<String, String>> = {
        permanentSessionProperties?.let { HashMap(it) } ?: HashMap()
    }

    private var permanentSessionProperties: Map<String, String>?
        get() = store.getStringMap(SESSION_PROPERTIES_KEY)
        set(value) = store.edit { putStringMap(SESSION_PROPERTIES_KEY, value) }

    private fun permanentProperties(): MutableMap<String, String> {
        if (permanentPropertiesReference.get() === NOT_LOADED) {
            synchronized(permanentPropertiesReference) {
                if (permanentPropertiesReference.get() === NOT_LOADED) {
                    permanentPropertiesReference.set(permanentPropertiesProvider())
                }
            }
        }

        return permanentPropertiesReference.get()
    }

    private fun haveKey(key: String): Boolean {
        return permanentProperties().containsKey(key) || temporary.containsKey(key)
    }

    fun add(sanitizedKey: String, sanitizedValue: String, isPermanent: Boolean): Boolean {
        synchronized(permanentPropertiesReference) {
            val maxSessionProperties = configService.sessionBehavior.getMaxSessionProperties()
            if (size() > maxSessionProperties || size() == maxSessionProperties && !haveKey(sanitizedKey)) {
                return false
            }

            // add to selected destination, deleting the key if it exists in the other destination
            if (isPermanent) {
                permanentProperties()[sanitizedKey] = sanitizedValue
                temporary.remove(sanitizedKey)
                permanentSessionProperties = permanentProperties()
            } else {
                // only save the permanent values if the key existed in the permanent map
                val newPermanent = permanentProperties()
                if (newPermanent.remove(sanitizedKey) != null) {
                    permanentPropertiesReference.set(newPermanent)
                    permanentSessionProperties = permanentProperties()
                }
                temporary[sanitizedKey] = sanitizedValue
            }
            destination.addSessionAttribute(
                sanitizedKey.toEmbraceAttributeName(),
                sanitizedValue
            )
            return true
        }
    }

    fun remove(sanitizedKey: String): Boolean {
        synchronized(permanentPropertiesReference) {
            var existed = false
            if (temporary.remove(sanitizedKey) != null) {
                existed = true
            }

            val newPermanent = permanentProperties()
            if (newPermanent.remove(sanitizedKey) != null) {
                permanentPropertiesReference.set(newPermanent)
                permanentSessionProperties = permanentProperties()
                existed = true
            }
            destination.removeSessionAttribute(sanitizedKey.toEmbraceAttributeName())
            return existed
        }
    }

    fun get(): Map<String, String> = synchronized(permanentPropertiesReference) {
        permanentProperties().plus(temporary)
    }

    private fun size(): Int = permanentProperties().size + temporary.size

    fun clear() {
        synchronized(permanentPropertiesReference) {
            temporary.clear()
        }
    }

    fun addPermPropsToSessionSpan() {
        permanentProperties().entries.forEach {
            destination.addSessionAttribute(
                it.key.toEmbraceAttributeName(),
                it.value
            )
        }
    }

    private companion object {
        private val NOT_LOADED = mutableMapOf<String, String>()
        private const val SESSION_PROPERTIES_KEY = "io.embrace.session.properties"
    }
}
