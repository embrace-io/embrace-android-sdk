package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal class SessionPropertiesServiceImpl(
    preferencesService: PreferencesService,
    configService: ConfigService,
    logger: EmbLogger,
    writer: SessionSpanWriter
) : SessionPropertiesService {

    private val props = EmbraceSessionProperties(preferencesService, configService, logger, writer)

    override fun addProperty(
        originalKey: String,
        originalValue: String,
        permanent: Boolean
    ): Boolean {
        if (!isValidKey(originalKey)) {
            return false
        }
        val sanitizedKey = enforceLength(originalKey, SESSION_PROPERTY_KEY_LIMIT)

        if (!isValidValue(originalValue)) {
            return false
        }
        val sanitizedValue = enforceLength(originalValue, SESSION_PROPERTY_VALUE_LIMIT)

        return props.add(sanitizedKey, sanitizedValue, permanent)
    }

    override fun removeProperty(originalKey: String): Boolean {
        if (!isValidKey(originalKey)) {
            return false
        }
        val sanitizedKey = enforceLength(originalKey, SESSION_PROPERTY_KEY_LIMIT)

        return props.remove(sanitizedKey)
    }

    override fun getProperties(): Map<String, String> = props.get()

    override fun prepareForNewSession() {
        props.prepareForNewSession()
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
