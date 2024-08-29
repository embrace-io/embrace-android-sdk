package io.embrace.android.embracesdk.internal.prefs

import android.content.SharedPreferences
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.lifecycle.StartupListener
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal class EmbracePreferencesService(
    private val backgroundWorker: BackgroundWorker,
    private val lazyPrefs: Lazy<SharedPreferences>,
    private val clock: Clock,
    private val serializer: PlatformSerializer
) : PreferencesService, StartupListener {

    // We get SharedPreferences on a background thread because it loads data from disk
    // and can block. When client code needs to set/get a preference, getSharedPrefs() will
    // block if necessary with Future.get(). Eagerly offloading buys us more time
    // for SharedPreferences to load the File and reduces the likelihood of blocking
    // when invoked by client code.
    private val preferences: Future<SharedPreferences> = Systrace.traceSynchronous("trigger-load-prefs") {
        backgroundWorker.submit(
            callable = Callable {
                Systrace.traceSynchronous("load-prefs") {
                    lazyPrefs.value
                }
            }
        )
    }

    init {
        alterStartupStatus(SDK_STARTUP_IN_PROGRESS)
    }

    override fun applicationStartupComplete(): Unit = alterStartupStatus(SDK_STARTUP_COMPLETED)

    private fun alterStartupStatus(status: String) {
        backgroundWorker.submit {
            Systrace.traceSynchronous("set-startup-status") {
                prefs.setStringPreference(SDK_STARTUP_STATUS_KEY, status)
            }
        }
    }

    // fallback from this very unlikely case by just loading on the main thread
    private val prefs: SharedPreferences
        get() = try {
            Systrace.traceSynchronous("get-prefs") {
                preferences.get(2, TimeUnit.SECONDS)
            }
        } catch (exc: Throwable) {
            // fallback from this very unlikely case by just loading on the main thread
            lazyPrefs.value
        }

    private fun SharedPreferences.getStringPreference(key: String): String? {
        return getString(key, null)
    }

    private fun SharedPreferences.setStringPreference(key: String, value: String?) {
        val editor = edit()
        editor.putString(key, value)
        editor.apply()
    }

    private fun SharedPreferences.getLongPreference(key: String): Long? {
        val defaultValue: Long = -1L
        return when (val value = getLong(key, defaultValue)) {
            defaultValue -> null
            else -> value
        }
    }

    private fun SharedPreferences.setLongPreference(key: String, value: Long?) {
        if (value != null) {
            val editor = edit()
            editor.putLong(key, value)
            editor.apply()
        }
    }

    private fun SharedPreferences.getIntegerPreference(key: String): Int? {
        val defaultValue: Int = -1
        return when (val value = getInt(key, defaultValue)) {
            defaultValue -> null
            else -> value
        }
    }

    private fun SharedPreferences.setIntegerPreference(key: String, value: Int) {
        val editor = edit()
        editor.putInt(key, value)
        editor.apply()
    }

    private fun SharedPreferences.getBooleanPreference(
        key: String,
        defaultValue: Boolean
    ): Boolean {
        return getBoolean(key, defaultValue)
    }

    private fun SharedPreferences.setBooleanPreference(
        key: String,
        value: Boolean?
    ) {
        if (value != null) {
            val editor = edit()
            editor.putBoolean(key, value)
            editor.apply()
        }
    }

    private fun SharedPreferences.setArrayPreference(
        key: String,
        value: Set<String>?
    ) {
        val editor = edit()
        editor.putStringSet(key, value)
        editor.apply()
    }

    private fun SharedPreferences.getArrayPreference(key: String): Set<String>? {
        return getStringSet(key, null)
    }

    private fun SharedPreferences.setMapPreference(
        key: String,
        value: Map<String, String>?
    ) {
        val editor = edit()
        val mapString = when (value) {
            null -> null
            else -> serializer.toJson(value, Map::class.java)
        }
        editor.putString(key, mapString)
        editor.apply()
    }

    @Suppress("UNCHECKED_CAST")
    private fun SharedPreferences.getMapPreference(
        key: String
    ): Map<String, String>? {
        val mapString = getString(key, null) ?: return null
        return serializer.fromJson(mapString, Map::class.java) as Map<String, String>
    }

    override var appVersion: String?
        get() = prefs.getStringPreference(PREVIOUS_APP_VERSION_KEY)
        set(value) = prefs.setStringPreference(PREVIOUS_APP_VERSION_KEY, value)

    override var osVersion: String?
        get() = prefs.getStringPreference(PREVIOUS_OS_VERSION_KEY)
        set(value) = prefs.setStringPreference(PREVIOUS_OS_VERSION_KEY, value)

    override var installDate: Long?
        get() = prefs.getLongPreference(INSTALL_DATE_KEY)
        set(value) = prefs.setLongPreference(INSTALL_DATE_KEY, value)

    override var deviceIdentifier: String
        get() {
            val deviceId = prefs.getStringPreference(DEVICE_IDENTIFIER_KEY)
            if (deviceId != null) {
                return deviceId
            }
            val newId = getEmbUuid()
            deviceIdentifier = newId
            return newId
        }
        set(value) = prefs.setStringPreference(DEVICE_IDENTIFIER_KEY, value)

    override val sdkStartupStatus: String?
        get() = prefs.getStringPreference(SDK_STARTUP_STATUS_KEY)

    override var sdkDisabled: Boolean
        get() = prefs.getBooleanPreference(SDK_DISABLED_KEY, false)
        set(value) = prefs.setBooleanPreference(SDK_DISABLED_KEY, value)

    override var userPayer: Boolean
        get() = prefs.getBooleanPreference(USER_IS_PAYER_KEY, false)
        set(value) = prefs.setBooleanPreference(USER_IS_PAYER_KEY, value)

    override var userIdentifier: String?
        get() = prefs.getStringPreference(USER_IDENTIFIER_KEY)
        set(value) = prefs.setStringPreference(USER_IDENTIFIER_KEY, value)

    override var userEmailAddress: String?
        get() = prefs.getStringPreference(USER_EMAIL_ADDRESS_KEY)
        set(value) = prefs.setStringPreference(USER_EMAIL_ADDRESS_KEY, value)

    override var userPersonas: Set<String>?
        get() = prefs.getArrayPreference(USER_PERSONAS_KEY)
        set(value) = prefs.setArrayPreference(USER_PERSONAS_KEY, value)

    override var permanentSessionProperties: Map<String, String>?
        get() = prefs.getMapPreference(SESSION_PROPERTIES_KEY)
        set(value) = prefs.setMapPreference(SESSION_PROPERTIES_KEY, value)

    @Deprecated("")
    override val customPersonas: Set<String>?
        get() = prefs.getArrayPreference(CUSTOM_PERSONAS_KEY)

    override var username: String?
        get() = prefs.getStringPreference(USER_USERNAME_KEY)
        set(value) = prefs.setStringPreference(USER_USERNAME_KEY, value)

    override var lastConfigFetchDate: Long?
        get() = prefs.getLongPreference(SDK_CONFIG_FETCHED_TIMESTAMP)
        set(value) = prefs.setLongPreference(SDK_CONFIG_FETCHED_TIMESTAMP, value)

    override var userMessageNeedsRetry: Boolean
        get() = prefs.getBooleanPreference(LAST_USER_MESSAGE_FAILED_KEY, false)
        set(value) = prefs.setBooleanPreference(LAST_USER_MESSAGE_FAILED_KEY, value)

    override fun incrementAndGetSessionNumber(): Int {
        return incrementAndGetOrdinal(LAST_SESSION_NUMBER_KEY)
    }

    override fun incrementAndGetBackgroundActivityNumber(): Int {
        return incrementAndGetOrdinal(LAST_BACKGROUND_ACTIVITY_NUMBER_KEY)
    }

    override fun incrementAndGetCrashNumber(): Int {
        return incrementAndGetOrdinal(LAST_CRASH_NUMBER_KEY)
    }

    override fun incrementAndGetNativeCrashNumber(): Int {
        return incrementAndGetOrdinal(LAST_NATIVE_CRASH_NUMBER_KEY)
    }

    private fun incrementAndGetOrdinal(key: String): Int {
        return try {
            val ordinal = (prefs.getIntegerPreference(key) ?: 0) + 1
            prefs.setIntegerPreference(key, ordinal)
            ordinal
        } catch (tr: Throwable) {
            -1
        }
    }

    override var javaScriptBundleURL: String?
        get() = prefs.getStringPreference(JAVA_SCRIPT_BUNDLE_URL_KEY)
        set(value) = prefs.setStringPreference(JAVA_SCRIPT_BUNDLE_URL_KEY, value)

    override var javaScriptBundleId: String?
        get() = prefs.getStringPreference(JAVA_SCRIPT_BUNDLE_ID_KEY)
        set(value) = prefs.setStringPreference(JAVA_SCRIPT_BUNDLE_ID_KEY, value)

    override var rnSdkVersion: String?
        get() = prefs.getStringPreference(REACT_NATIVE_SDK_VERSION_KEY)
        set(value) = prefs.setStringPreference(REACT_NATIVE_SDK_VERSION_KEY, value)

    override var javaScriptPatchNumber: String?
        get() = prefs.getStringPreference(JAVA_SCRIPT_PATCH_NUMBER_KEY)
        set(value) = prefs.setStringPreference(JAVA_SCRIPT_PATCH_NUMBER_KEY, value)

    override var reactNativeVersionNumber: String?
        get() = prefs.getStringPreference(REACT_NATIVE_VERSION_KEY)
        set(value) = prefs.setStringPreference(REACT_NATIVE_VERSION_KEY, value)

    override var unityVersionNumber: String?
        get() = prefs.getStringPreference(UNITY_VERSION_NUMBER_KEY)
        set(value) = prefs.setStringPreference(UNITY_VERSION_NUMBER_KEY, value)

    override var unityBuildIdNumber: String?
        get() = prefs.getStringPreference(UNITY_BUILD_ID_NUMBER_KEY)
        set(value) = prefs.setStringPreference(UNITY_BUILD_ID_NUMBER_KEY, value)

    override var unitySdkVersionNumber: String?
        get() = prefs.getStringPreference(UNITY_SDK_VERSION_NUMBER_KEY)
        set(value) = prefs.setStringPreference(UNITY_SDK_VERSION_NUMBER_KEY, value)

    override var dartSdkVersion: String?
        get() = prefs.getStringPreference(DART_SDK_VERSION_KEY)
        set(value) = prefs.setStringPreference(DART_SDK_VERSION_KEY, value)

    override var embraceFlutterSdkVersion: String?
        get() = prefs.getStringPreference(EMBRACE_FLUTTER_SDK_VERSION_KEY)
        set(value) = prefs.setStringPreference(EMBRACE_FLUTTER_SDK_VERSION_KEY, value)

    override var jailbroken: Boolean?
        get() = when {
            !prefs.contains(IS_JAILBROKEN_KEY) -> null
            else -> prefs.getBooleanPreference(
                IS_JAILBROKEN_KEY,
                false
            )
        }
        set(value) = prefs.setBooleanPreference(IS_JAILBROKEN_KEY, value)

    override var screenResolution: String?
        get() = prefs.getStringPreference(SCREEN_RESOLUTION_KEY)
        set(value) = prefs.setStringPreference(SCREEN_RESOLUTION_KEY, value)

    override var backgroundActivityEnabled: Boolean
        get() = prefs.getBooleanPreference(BACKGROUND_ACTIVITY_ENABLED_KEY, false)
        set(value) = prefs.setBooleanPreference(BACKGROUND_ACTIVITY_ENABLED_KEY, value)

    override var applicationExitInfoHistory: Set<String>?
        get() = prefs.getStringSet(AEI_HASH_CODES, null)
        set(value) = prefs.setArrayPreference(AEI_HASH_CODES, value)

    override var cpuName: String?
        get() = prefs.getStringPreference(CPU_NAME_KEY)
        set(value) = prefs.setStringPreference(CPU_NAME_KEY, value)

    override var egl: String?
        get() = prefs.getStringPreference(EGL_KEY)
        set(value) = prefs.setStringPreference(EGL_KEY, value)

    override fun isUsersFirstDay(): Boolean {
        val installDate = installDate
        return installDate != null && clock.now() - installDate <= PreferencesService.DAY_IN_MS
    }

    override fun isNetworkCaptureRuleOver(id: String): Boolean {
        return getNetworkCaptureRuleRemainingCount(id) <= 0
    }

    override fun decreaseNetworkCaptureRuleRemainingCount(id: String, maxCount: Int) {
        prefs.setIntegerPreference(
            NETWORK_CAPTURE_RULE_PREFIX_KEY + id,
            getNetworkCaptureRuleRemainingCount(id, maxCount) - 1
        )
    }

    private fun getNetworkCaptureRuleRemainingCount(id: String): Int {
        return getNetworkCaptureRuleRemainingCount(id, 1)
    }

    private fun getNetworkCaptureRuleRemainingCount(id: String, maxCount: Int): Int {
        val value = prefs.getIntegerPreference(NETWORK_CAPTURE_RULE_PREFIX_KEY + id)
        return value ?: maxCount
    }

    public companion object {
        internal const val SDK_STARTUP_IN_PROGRESS = "startup_entered"
        internal const val SDK_STARTUP_COMPLETED = "startup_completed"
        private const val SDK_STARTUP_STATUS_KEY = "io.embrace.sdkstartup"
        private const val DEVICE_IDENTIFIER_KEY = "io.embrace.deviceid"
        private const val PREVIOUS_APP_VERSION_KEY = "io.embrace.lastappversion"
        private const val PREVIOUS_OS_VERSION_KEY = "io.embrace.lastosversion"
        private const val INSTALL_DATE_KEY = "io.embrace.installtimestamp"
        private const val USER_IDENTIFIER_KEY = "io.embrace.userid"
        private const val USER_EMAIL_ADDRESS_KEY = "io.embrace.useremail"
        private const val USER_USERNAME_KEY = "io.embrace.username"
        private const val USER_IS_PAYER_KEY = "io.embrace.userispayer"
        private const val USER_PERSONAS_KEY = "io.embrace.userpersonas"
        private const val CUSTOM_PERSONAS_KEY = "io.embrace.custompersonas"
        private const val LAST_USER_MESSAGE_FAILED_KEY = "io.embrace.userupdatefailed"
        private const val LAST_SESSION_NUMBER_KEY = "io.embrace.sessionnumber"
        private const val LAST_BACKGROUND_ACTIVITY_NUMBER_KEY = "io.embrace.bgactivitynumber"
        private const val LAST_CRASH_NUMBER_KEY = "io.embrace.crashnumber"
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
        private const val BACKGROUND_ACTIVITY_ENABLED_KEY = "io.embrace.bgactivitycapture"
        private const val NETWORK_CAPTURE_RULE_PREFIX_KEY = "io.embrace.networkcapturerule"
        private const val SDK_DISABLED_KEY = "io.embrace.disabled"
        private const val SDK_CONFIG_FETCHED_TIMESTAMP = "io.embrace.sdkfetchedtimestamp"
        private const val AEI_HASH_CODES = "io.embrace.aeiHashCode"
        private const val CPU_NAME_KEY = "io.embrace.cpuName"
        private const val EGL_KEY = "io.embrace.egl"
    }
}
