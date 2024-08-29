package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.concurrent.atomic.AtomicReference

internal class EmbraceSessionProperties(
    private val preferencesService: PreferencesService,
    private val configService: ConfigService,
    private val logger: EmbLogger,
    private val writer: SessionSpanWriter
) {
    private val temporary: MutableMap<String, String> = HashMap()
    private val permanentPropertiesReference = AtomicReference(NOT_LOADED)
    private val permanentPropertiesProvider: Provider<MutableMap<String, String>> = {
        preferencesService.permanentSessionProperties?.let(::HashMap) ?: HashMap()
    }

    private fun permanentProperties(): MutableMap<String, String> {
        if (permanentPropertiesReference.get() === NOT_LOADED) {
            synchronized(permanentPropertiesReference) {
                if (permanentPropertiesReference.get() === NOT_LOADED) {
                    permanentPropertiesReference.set(permanentPropertiesProvider())
                    addPermPropsToSessionSpan()
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
                logger.logError("Session property count is at its limit. Rejecting.")
                return false
            }

            // add to selected destination, deleting the key if it exists in the other destination
            if (isPermanent) {
                permanentProperties()[sanitizedKey] = sanitizedValue
                temporary.remove(sanitizedKey)
                preferencesService.permanentSessionProperties = permanentProperties()
            } else {
                // only save the permanent values if the key existed in the permanent map
                val newPermanent = permanentProperties()
                if (newPermanent.remove(sanitizedKey) != null) {
                    permanentPropertiesReference.set(newPermanent)
                    preferencesService.permanentSessionProperties = permanentProperties()
                }
                temporary[sanitizedKey] = sanitizedValue
            }
            writer.addSystemAttribute(
                SpanAttributeData(
                    sanitizedKey.toSessionPropertyAttributeName(),
                    sanitizedValue
                )
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
                preferencesService.permanentSessionProperties = permanentProperties()
                existed = true
            }
            writer.removeSystemAttribute(sanitizedKey.toSessionPropertyAttributeName())
            return existed
        }
    }

    fun get(): Map<String, String> = synchronized(permanentPropertiesReference) {
        permanentProperties().plus(temporary)
    }

    private fun size(): Int = permanentProperties().size + temporary.size

    fun prepareForNewSession() {
        synchronized(permanentPropertiesReference) {
            temporary.clear()
            addPermPropsToSessionSpan()
        }
    }

    private fun addPermPropsToSessionSpan() {
        permanentPropertiesReference.get().entries.forEach {
            writer.addSystemAttribute(
                SpanAttributeData(
                    it.key.toSessionPropertyAttributeName(),
                    it.value
                )
            )
        }
    }

    private companion object {
        private val NOT_LOADED = mutableMapOf<String, String>()
    }
}
