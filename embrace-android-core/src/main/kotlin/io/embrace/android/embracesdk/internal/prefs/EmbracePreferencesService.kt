package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid

internal class EmbracePreferencesService(
    private val impl: KeyValueStore,
) : PreferencesService {

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

    override var javaScriptBundleURL: String?
        get() = impl.getString(JAVA_SCRIPT_BUNDLE_URL_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_BUNDLE_URL_KEY, value) }

    override var javaScriptBundleId: String?
        get() = impl.getString(JAVA_SCRIPT_BUNDLE_ID_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_BUNDLE_ID_KEY, value) }

    companion object {
        private const val DEVICE_IDENTIFIER_KEY = "io.embrace.deviceid"
        private const val JAVA_SCRIPT_BUNDLE_URL_KEY = "io.embrace.jsbundle.url"
        private const val JAVA_SCRIPT_BUNDLE_ID_KEY = "io.embrace.jsbundle.id"
    }
}
