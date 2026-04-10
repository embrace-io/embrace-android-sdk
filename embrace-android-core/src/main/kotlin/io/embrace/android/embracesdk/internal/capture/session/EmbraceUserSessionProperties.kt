package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService

internal class EmbraceUserSessionProperties(
    private val store: KeyValueStore,
    configService: ConfigService,
    private val destination: TelemetryDestination,
    private val telemetryService: TelemetryService,
) {
    private data class PropertyEntry(val value: String, val scope: PropertyScope)

    private val propertyLimit = configService.sessionBehavior.getMaxUserSessionProperties()

    private val lock = Any()

    private val properties: MutableMap<String, PropertyEntry> by lazy {
        store.getStringMap(SESSION_PROPERTIES_KEY)?.mapValues {
            PropertyEntry(it.value, PropertyScope.PERMANENT)
        }?.toMutableMap() ?: mutableMapOf()
    }

    private fun persistPermanentProperties() {
        store.edit {
            putStringMap(
                SESSION_PROPERTIES_KEY,
                properties.entries
                    .filter { it.value.scope == PropertyScope.PERMANENT }
                    .associate { it.key to it.value.value }
            )
        }
    }

    /**
     * Adds a user session property, returning true if it was successfully added.
     */
    fun add(sanitizedKey: String, sanitizedValue: String, scope: PropertyScope): Boolean {
        synchronized(lock) {
            val totalCount = properties.size
            if (totalCount > propertyLimit || totalCount == propertyLimit && !properties.containsKey(sanitizedKey)) {
                telemetryService.trackAppliedLimit("session_property", AppliedLimitType.DROP)
                return false
            }
            val previousScope = properties[sanitizedKey]?.scope
            properties[sanitizedKey] = PropertyEntry(sanitizedValue, scope)
            if (scope == PropertyScope.PERMANENT || previousScope == PropertyScope.PERMANENT) {
                persistPermanentProperties()
            }
            destination.addSessionPartAttribute(
                sanitizedKey.toEmbraceAttributeName(),
                sanitizedValue
            )
            return true
        }
    }

    /**
     * Removes a user session property, returning true if it was removed.
     */
    fun remove(sanitizedKey: String): Boolean {
        synchronized(lock) {
            val entry = properties.remove(sanitizedKey) ?: return false
            if (entry.scope == PropertyScope.PERMANENT) {
                persistPermanentProperties()
            }
            destination.removeSessionPartAttribute(sanitizedKey.toEmbraceAttributeName())
            return true
        }
    }

    /**
     * Gets a Map representation of all current user session properties.
     */
    fun get(): Map<String, String> = synchronized(lock) {
        properties.mapValues { it.value.value }
    }

    /**
     * Notify that a new user session has occurred so that old user session properties can be cleared.
     */
    fun onNewUserSession() {
        synchronized(lock) {
            val iter = properties.iterator()
            while (iter.hasNext()) {
                if (iter.next().value.scope == PropertyScope.USER_SESSION) {
                    iter.remove()
                }
            }
        }
    }

    /**
     * Adds all user session properties to the session span.
     */
    fun addPropsForNewSessionSpan() {
        synchronized(lock) {
            properties.forEach { (key, entry) ->
                if (entry.scope != PropertyScope.USER_SESSION) {
                    destination.addSessionPartAttribute(key.toEmbraceAttributeName(), entry.value)
                }
            }
        }
    }

    private companion object {
        private const val SESSION_PROPERTIES_KEY = "io.embrace.session.properties"
    }
}
