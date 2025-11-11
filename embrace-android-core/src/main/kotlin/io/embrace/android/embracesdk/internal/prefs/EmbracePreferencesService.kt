package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid

internal class EmbracePreferencesService(
    private val impl: KeyValueStore,
    private val clock: Clock,
) : PreferencesService {

    override var appVersion: String?
        get() = impl.getString(PREVIOUS_APP_VERSION_KEY)
        set(value) = impl.edit { putString(PREVIOUS_APP_VERSION_KEY, value) }

    override var osVersion: String?
        get() = impl.getString(PREVIOUS_OS_VERSION_KEY)
        set(value) = impl.edit { putString(PREVIOUS_OS_VERSION_KEY, value) }

    override var installDate: Long?
        get() = impl.getLong(INSTALL_DATE_KEY)
        set(value) = impl.edit { putLong(INSTALL_DATE_KEY, value) }

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

    override var userPayer: Boolean
        get() = impl.getBoolean(USER_IS_PAYER_KEY, false)
        set(value) = impl.edit { putBoolean(USER_IS_PAYER_KEY, value) }

    override var userIdentifier: String?
        get() = impl.getString(USER_IDENTIFIER_KEY)
        set(value) = impl.edit { putString(USER_IDENTIFIER_KEY, value) }

    override var userEmailAddress: String?
        get() = impl.getString(USER_EMAIL_ADDRESS_KEY)
        set(value) = impl.edit { putString(USER_EMAIL_ADDRESS_KEY, value) }

    override var userPersonas: Set<String>?
        get() = impl.getStringSet(USER_PERSONAS_KEY)
        set(value) = impl.edit { putStringSet(USER_PERSONAS_KEY, value) }

    override var permanentSessionProperties: Map<String, String>?
        get() = impl.getStringMap(SESSION_PROPERTIES_KEY)
        set(value) = impl.edit { putStringMap(SESSION_PROPERTIES_KEY, value) }

    override var username: String?
        get() = impl.getString(USER_USERNAME_KEY)
        set(value) = impl.edit { putString(USER_USERNAME_KEY, value) }

    override var lastConfigFetchDate: Long?
        get() = impl.getLong(SDK_CONFIG_FETCHED_TIMESTAMP)
        set(value) = impl.edit { putLong(SDK_CONFIG_FETCHED_TIMESTAMP, value) }

    override var javaScriptBundleURL: String?
        get() = impl.getString(JAVA_SCRIPT_BUNDLE_URL_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_BUNDLE_URL_KEY, value) }

    override var javaScriptBundleId: String?
        get() = impl.getString(JAVA_SCRIPT_BUNDLE_ID_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_BUNDLE_ID_KEY, value) }

    override fun isUsersFirstDay(): Boolean {
        val installDate = installDate
        return installDate != null && clock.now() - installDate <= PreferencesService.DAY_IN_MS
    }

    companion object {
        private const val DEVICE_IDENTIFIER_KEY = "io.embrace.deviceid"
        private const val PREVIOUS_APP_VERSION_KEY = "io.embrace.lastappversion"
        private const val PREVIOUS_OS_VERSION_KEY = "io.embrace.lastosversion"
        private const val INSTALL_DATE_KEY = "io.embrace.installtimestamp"
        private const val USER_IDENTIFIER_KEY = "io.embrace.userid"
        private const val USER_EMAIL_ADDRESS_KEY = "io.embrace.useremail"
        private const val USER_USERNAME_KEY = "io.embrace.username"
        private const val USER_IS_PAYER_KEY = "io.embrace.userispayer"
        private const val USER_PERSONAS_KEY = "io.embrace.userpersonas"
        private const val JAVA_SCRIPT_BUNDLE_URL_KEY = "io.embrace.jsbundle.url"
        private const val JAVA_SCRIPT_BUNDLE_ID_KEY = "io.embrace.jsbundle.id"
        private const val SESSION_PROPERTIES_KEY = "io.embrace.session.properties"
        private const val SDK_CONFIG_FETCHED_TIMESTAMP = "io.embrace.sdkfetchedtimestamp"
    }
}
