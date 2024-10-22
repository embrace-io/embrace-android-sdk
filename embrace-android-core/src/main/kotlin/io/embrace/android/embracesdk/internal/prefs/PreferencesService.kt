package io.embrace.android.embracesdk.internal.prefs

interface PreferencesService {

    /**
     * The last registered Host App version name
     */
    var appVersion: String?

    /**
     * The last registered OS Version
     */
    var osVersion: String?

    /**
     * The app install date in ms
     */
    var installDate: Long?

    /**
     * The unique identifier for this device.
     */
    var deviceIdentifier: String

    /**
     * If the sdk is disabled
     */
    var sdkDisabled: Boolean

    /**
     * If the user is payer
     */
    var userPayer: Boolean

    /**
     * User unique identifier
     */
    var userIdentifier: String?

    /**
     * User email address
     */
    var userEmailAddress: String?

    /**
     * Personas for the user
     */
    var userPersonas: Set<String>?

    /**
     * All permanent session properties
     */
    var permanentSessionProperties: Map<String, String>?

    /**
     * Username for the user
     */
    var username: String?

    /**
     * The last time config was fetched from the server
     */
    var lastConfigFetchDate: Long?

    /**
     * If the user message needs to retry send
     */
    var userMessageNeedsRetry: Boolean

    /**
     * Increments and returns the session number ordinal. This is an integer that increments
     * at the start of every session. This allows us to check the % of sessions that didn't get
     * delivered to the backend.
     */
    fun incrementAndGetSessionNumber(): Int

    /**
     * Increments and returns the background activity number ordinal. This is an integer that
     * increments at the start of every background activity. This allows us to check the % of
     * requests that didn't get delivered to the backend.
     */
    fun incrementAndGetBackgroundActivityNumber(): Int

    /**
     * Increments and returns the crash number ordinal. This is an integer that
     * increments on every crash. It allows us to check the % of crashes that
     * didn't get delivered to the backend.
     */
    fun incrementAndGetCrashNumber(): Int

    /**
     * Increments and returns the native crash number ordinal. This is an integer that
     * increments on every native crash. It allows us to check the % of native crashes that
     * didn't get delivered to the backend.
     */
    fun incrementAndGetNativeCrashNumber(): Int

    /**
     * Last javaScript bundle string url.
     */
    var javaScriptBundleURL: String?

    /**
     * Last javaScript bundle ID.
     */
    var javaScriptBundleId: String?

    /**
     * Embrace sdk version.
     */
    var rnSdkVersion: String?

    /**
     * Last javaScript patch string number.
     */
    var javaScriptPatchNumber: String?

    /**
     * Last react native version.
     */
    var reactNativeVersionNumber: String?

    /**
     * Last Unity version.
     */
    var unityVersionNumber: String?

    /**
     * Last Unity Build ID
     */
    var unityBuildIdNumber: String?

    /**
     * Last Unity SDK version
     */
    var unitySdkVersionNumber: String?

    /**
     * Last Flutter SDK version
     */
    var embraceFlutterSdkVersion: String?

    /**
     * Last Dart SDK version
     */
    var dartSdkVersion: String?

    /**
     * If the device is a rooted device.
     */
    var jailbroken: Boolean?

    /**
     * The device's screen resolution.
     */
    var screenResolution: String?

    /**
     * The device's cpu name.
     */
    var cpuName: String?

    /**
     * The device's egl.
     */
    var egl: String?

    /**
     * If background activity capture is enabled
     */
    var backgroundActivityEnabled: Boolean

    /**
     * Set of hashcodes derived from ApplicationExitInfo objects
     */
    var applicationExitInfoHistory: Set<String>?

    /**
     * Whether or not the app was installed within the last 24 hours.
     *
     * @return true if it is the user's first day, false otherwise
     */
    fun isUsersFirstDay(): Boolean

    /**
     * Ssuffix to compose the key to get the stored value
     */
    fun isNetworkCaptureRuleOver(id: String): Boolean

    /**
     * Suffix to compose the key to get the stored value
     */
    fun decreaseNetworkCaptureRuleRemainingCount(id: String, maxCount: Int)

    companion object {
        const val DAY_IN_MS: Long = 60 * 60 * 24 * 1000L
    }
}
