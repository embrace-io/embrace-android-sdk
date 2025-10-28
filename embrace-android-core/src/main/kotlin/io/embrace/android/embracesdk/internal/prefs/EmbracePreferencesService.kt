package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.arch.store.KeyValueStore
import io.embrace.android.embracesdk.internal.clock.Clock
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

    override fun incrementAndGetSessionNumber(): Int {
        return impl.incrementAndGet(LAST_SESSION_NUMBER_KEY)
    }

    override fun incrementAndGetBackgroundActivityNumber(): Int {
        return impl.incrementAndGet(LAST_BACKGROUND_ACTIVITY_NUMBER_KEY)
    }

    override fun incrementAndGetCrashNumber(): Int {
        return impl.incrementAndGetCrashNumber()
    }

    override fun incrementAndGetNativeCrashNumber(): Int {
        return impl.incrementAndGet(LAST_NATIVE_CRASH_NUMBER_KEY)
    }

    override var javaScriptBundleURL: String?
        get() = impl.getString(JAVA_SCRIPT_BUNDLE_URL_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_BUNDLE_URL_KEY, value) }

    override var javaScriptBundleId: String?
        get() = impl.getString(JAVA_SCRIPT_BUNDLE_ID_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_BUNDLE_ID_KEY, value) }

    override var rnSdkVersion: String?
        get() = impl.getString(REACT_NATIVE_SDK_VERSION_KEY)
        set(value) = impl.edit { putString(REACT_NATIVE_SDK_VERSION_KEY, value) }

    override var javaScriptPatchNumber: String?
        get() = impl.getString(JAVA_SCRIPT_PATCH_NUMBER_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_PATCH_NUMBER_KEY, value) }

    override var reactNativeVersionNumber: String?
        get() = impl.getString(REACT_NATIVE_VERSION_KEY)
        set(value) = impl.edit { putString(REACT_NATIVE_VERSION_KEY, value) }

    override var unityVersionNumber: String?
        get() = impl.getString(UNITY_VERSION_NUMBER_KEY)
        set(value) = impl.edit { putString(UNITY_VERSION_NUMBER_KEY, value) }

    override var unityBuildIdNumber: String?
        get() = impl.getString(UNITY_BUILD_ID_NUMBER_KEY)
        set(value) = impl.edit { putString(UNITY_BUILD_ID_NUMBER_KEY, value) }

    override var unitySdkVersionNumber: String?
        get() = impl.getString(UNITY_SDK_VERSION_NUMBER_KEY)
        set(value) = impl.edit { putString(UNITY_SDK_VERSION_NUMBER_KEY, value) }

    override var dartSdkVersion: String?
        get() = impl.getString(DART_SDK_VERSION_KEY)
        set(value) = impl.edit { putString(DART_SDK_VERSION_KEY, value) }

    override var embraceFlutterSdkVersion: String?
        get() = impl.getString(EMBRACE_FLUTTER_SDK_VERSION_KEY)
        set(value) = impl.edit { putString(EMBRACE_FLUTTER_SDK_VERSION_KEY, value) }

    override var jailbroken: Boolean?
        get() = impl.getBoolean(
            IS_JAILBROKEN_KEY,
            false
        )
        set(value) = impl.edit { putBoolean(IS_JAILBROKEN_KEY, value) }

    override var screenResolution: String?
        get() = impl.getString(SCREEN_RESOLUTION_KEY)
        set(value) = impl.edit { putString(SCREEN_RESOLUTION_KEY, value) }

    override fun isUsersFirstDay(): Boolean {
        val installDate = installDate
        return installDate != null && clock.now() - installDate <= PreferencesService.DAY_IN_MS
    }

    override fun isNetworkCaptureRuleOver(id: String): Boolean {
        return getNetworkCaptureRuleRemainingCount(id) <= 0
    }

    override fun decreaseNetworkCaptureRuleRemainingCount(id: String, maxCount: Int) {
        impl.edit {
            putInt(NETWORK_CAPTURE_RULE_PREFIX_KEY + id, getNetworkCaptureRuleRemainingCount(id, maxCount) - 1)
        }
    }

    private fun getNetworkCaptureRuleRemainingCount(id: String): Int {
        return getNetworkCaptureRuleRemainingCount(id, 1)
    }

    private fun getNetworkCaptureRuleRemainingCount(id: String, maxCount: Int): Int {
        val value = impl.getInt(NETWORK_CAPTURE_RULE_PREFIX_KEY + id)
        return value ?: maxCount
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
        private const val LAST_SESSION_NUMBER_KEY = "io.embrace.sessionnumber"
        private const val LAST_BACKGROUND_ACTIVITY_NUMBER_KEY = "io.embrace.bgactivitynumber"
        private const val LAST_NATIVE_CRASH_NUMBER_KEY = "io.embrace.nativecrashnumber"
        private const val JAVA_SCRIPT_BUNDLE_URL_KEY = "io.embrace.jsbundle.url"
        private const val JAVA_SCRIPT_BUNDLE_ID_KEY = "io.embrace.jsbundle.id"
        private const val JAVA_SCRIPT_PATCH_NUMBER_KEY = "io.embrace.javascript.patch"
        private const val REACT_NATIVE_VERSION_KEY = "io.embrace.reactnative.version"
        private const val REACT_NATIVE_SDK_VERSION_KEY = "io.embrace.reactnative.sdk.version"
        private const val SESSION_PROPERTIES_KEY = "io.embrace.session.properties"
        private const val UNITY_VERSION_NUMBER_KEY = "io.embrace.unity.version"
        private const val UNITY_BUILD_ID_NUMBER_KEY = "io.embrace.unity.build.id"
        private const val UNITY_SDK_VERSION_NUMBER_KEY = "io.embrace.unity.sdk.version"
        private const val DART_SDK_VERSION_KEY = "io.embrace.dart.sdk.version"
        private const val EMBRACE_FLUTTER_SDK_VERSION_KEY = "io.embrace.flutter.sdk.version"
        private const val IS_JAILBROKEN_KEY = "io.embrace.is_jailbroken"
        private const val SCREEN_RESOLUTION_KEY = "io.embrace.screen.resolution"
        private const val NETWORK_CAPTURE_RULE_PREFIX_KEY = "io.embrace.networkcapturerule"
        private const val SDK_CONFIG_FETCHED_TIMESTAMP = "io.embrace.sdkfetchedtimestamp"
    }
}
