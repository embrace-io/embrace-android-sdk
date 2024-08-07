package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.utils.Provider

public class SessionPropertiesServiceImpl(
    preferencesService: PreferencesService,
    configService: ConfigService,
    logger: EmbLogger,
    private val dataSourceProvider: Provider<SessionPropertiesDataSource?>
) : SessionPropertiesService {

    private var listener: ((Map<String, String>) -> Unit)? = null
    private val props = EmbraceSessionProperties(preferencesService, configService, logger)

    override fun addProperty(originalKey: String, originalValue: String, permanent: Boolean): Boolean {
        if (!isValidKey(originalKey)) {
            return false
        }
        val sanitizedKey = enforceLength(originalKey, SESSION_PROPERTY_KEY_LIMIT)

        if (!isValidValue(originalValue)) {
            return false
        }
        val sanitizedValue = enforceLength(originalValue, SESSION_PROPERTY_VALUE_LIMIT)

        val added = props.add(sanitizedKey, sanitizedValue, permanent)
        if (added) {
            dataSourceProvider()?.addProperty(sanitizedKey, sanitizedValue)
            listener?.invoke(props.get())
        }
        return added
    }

    override fun removeProperty(originalKey: String): Boolean {
        if (!isValidKey(originalKey)) {
            return false
        }
        val sanitizedKey = enforceLength(originalKey, SESSION_PROPERTY_KEY_LIMIT)

        val removed = props.remove(sanitizedKey)
        if (removed) {
            dataSourceProvider()?.removeProperty(sanitizedKey)
            listener?.invoke(props.get())
        }
        return removed
    }

    override fun getProperties(): Map<String, String> = props.get()

    override fun populateCurrentSession(): Boolean {
        return dataSourceProvider()?.addProperties(getProperties()) ?: false
    }

    override fun clearTemporary() {
        val removedProps = props.clearTemporary()
        removedProps.forEach { (key, _) ->
            dataSourceProvider()?.removeProperty(key)
        }
    }

    override fun addChangeListener(listener: (Map<String, String>) -> Unit) {
        this.listener = listener
    }

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
