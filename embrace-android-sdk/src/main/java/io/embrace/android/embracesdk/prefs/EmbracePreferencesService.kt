package io.embrace.android.embracesdk.prefs

import android.content.SharedPreferences
import com.google.gson.reflect.TypeToken
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

internal class EmbracePreferencesService(
    registrationExecutorService: ExecutorService,
    lazyPrefs: Lazy<SharedPreferences>,
    private val clock: Clock,
    private val serializer: EmbraceSerializer
) : PreferencesService, ActivityLifecycleListener {

    private val preferences: Future<SharedPreferences>
    private val registrationExecutorService: ExecutorService
    private val lazyPrefs: Lazy<SharedPreferences>

    init {
        this.registrationExecutorService = registrationExecutorService
        this.lazyPrefs = lazyPrefs

        // We get SharedPreferences on a background thread because it loads data from disk
        // and can block. When client code needs to set/get a preference, getSharedPrefs() will
        // block if necessary with Future.get(). Eagerly offloading buys us more time
        // for SharedPreferences to load the File and reduces the likelihood of blocking
        // when invoked by client code.
        preferences = registrationExecutorService.submit(lazyPrefs::value)
        alterStartupStatus(SDK_STARTUP_IN_PROGRESS)
    }

    override fun applicationStartupComplete() = alterStartupStatus(SDK_STARTUP_COMPLETED)

    private fun alterStartupStatus(status: String) {
        registrationExecutorService.submit(
            Callable<Any?> {
                logDeveloper("EmbracePreferencesService", "Startup key: $status")
                prefs.setStringPreference(SDK_STARTUP_STATUS_KEY, status)
                null
            }
        )
    }

    // fallback from this very unlikely case by just loading on the main thread
    private val prefs: SharedPreferences
        get() = try {
            preferences.get()
        } catch (exc: Throwable) {
            // fallback from this very unlikely case by just loading on the main thread
            lazyPrefs.value
        }

    private fun SharedPreferences.getStringPreference(key: String): String? {
        return getString(key, null)
    }

    private fun SharedPreferences.setStringPreference(key: String, value: String?) {
        logDeveloper("EmbracePreferencesService", "Set $key: ${value ?: ""}")
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
        logDeveloper("EmbracePreferencesService", "Set $key: ${value ?: ""}")

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
        logDeveloper("EmbracePreferencesService", "Set $key: $value")
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
        logDeveloper("EmbracePreferencesService", "Set $key: ${value ?: ""}")
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
        logDeveloper("EmbracePreferencesService", "Set $key: ${value ?: ""}")
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
        logDeveloper("EmbracePreferencesService", "Set $key: ${value ?: ""}")
        val editor = edit()
        val mapString = when (value) {
            null -> null
            else -> serializer.toJson(value)
        }
        editor.putString(key, mapString)
        editor.apply()
    }

    private fun SharedPreferences.getMapPreference(
        key: String
    ): Map<String, String>? {
        val mapString = getString(key, null) ?: return null
        val type = object : TypeToken<HashMap<String?, String?>?>() {}.type
        return serializer.fromJson<HashMap<String, String>>(mapString, type)
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
            logDeveloper(
                "EmbracePreferencesService",
                "Device ID is null, created new one: $newId"
            )
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

    private fun incrementAndGetOrdinal(key: String): Int {
        val ordinal = (prefs.getIntegerPreference(key) ?: 0) + 1
        prefs.setIntegerPreference(key, ordinal)
        return ordinal
    }

    override var javaScriptBundleURL: String?
        get() = prefs.getStringPreference(JAVA_SCRIPT_BUNDLE_URL_KEY)
        set(value) = prefs.setStringPreference(JAVA_SCRIPT_BUNDLE_URL_KEY, value)

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

    companion object {
        const val SDK_STARTUP_IN_PROGRESS = "startup_entered"
        const val SDK_STARTUP_COMPLETED = "startup_completed"
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
        private const val JAVA_SCRIPT_BUNDLE_URL_KEY = "io.embrace.jsbundle.url"
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
