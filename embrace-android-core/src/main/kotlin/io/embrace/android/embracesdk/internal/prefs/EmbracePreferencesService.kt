package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid

internal class EmbracePreferencesService(
    private val impl: KeyValueStore,
) : PreferencesService {

    override var appVersion: String?
        get() = impl.getString(PREVIOUS_APP_VERSION_KEY)
        set(value) = impl.edit { putString(PREVIOUS_APP_VERSION_KEY, value) }

    override var osVersion: String?
        get() = impl.getString(PREVIOUS_OS_VERSION_KEY)
        set(value) = impl.edit { putString(PREVIOUS_OS_VERSION_KEY, value) }

    override var deviceIdentifier: String
        get() {
            val deviceId = impl.getString(DEVICE_IDENTIFIER_KEY)
            if (deviceId != null) {
                return deviceId
            }
            val newId = getEmbUuid()
            deviceIdentifier = newId
            return newId
        }
        set(value) = impl.edit { putString(DEVICE_IDENTIFIER_KEY, value) }

    override var permanentSessionProperties: Map<String, String>?
        get() = impl.getStringMap(SESSION_PROPERTIES_KEY)
        set(value) = impl.edit { putStringMap(SESSION_PROPERTIES_KEY, value) }

    override var lastConfigFetchDate: Long?
        get() = impl.getLong(SDK_CONFIG_FETCHED_TIMESTAMP)
        set(value) = impl.edit { putLong(SDK_CONFIG_FETCHED_TIMESTAMP, value) }

    override var javaScriptBundleURL: String?
        get() = impl.getString(JAVA_SCRIPT_BUNDLE_URL_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_BUNDLE_URL_KEY, value) }

    override var javaScriptBundleId: String?
        get() = impl.getString(JAVA_SCRIPT_BUNDLE_ID_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_BUNDLE_ID_KEY, value) }

    companion object {
        private const val DEVICE_IDENTIFIER_KEY = "io.embrace.deviceid"
        private const val PREVIOUS_APP_VERSION_KEY = "io.embrace.lastappversion"
        private const val PREVIOUS_OS_VERSION_KEY = "io.embrace.lastosversion"
        private const val JAVA_SCRIPT_BUNDLE_URL_KEY = "io.embrace.jsbundle.url"
        private const val JAVA_SCRIPT_BUNDLE_ID_KEY = "io.embrace.jsbundle.id"
        private const val SESSION_PROPERTIES_KEY = "io.embrace.session.properties"
        private const val SDK_CONFIG_FETCHED_TIMESTAMP = "io.embrace.sdkfetchedtimestamp"
    }
}
