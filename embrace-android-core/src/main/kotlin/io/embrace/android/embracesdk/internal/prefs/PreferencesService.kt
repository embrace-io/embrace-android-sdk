package io.embrace.android.embracesdk.internal.prefs

public interface PreferencesService {

    /**
     * The last registered Host App version name
     */
    public var appVersion: String?

    /**
     * The last registered OS Version
     */
    public var osVersion: String?

    /**
     * The app install date in ms
     */
    public var installDate: Long?

    /**
     * The unique identifier for this device.
     */
    public var deviceIdentifier: String

    /**
     * The last SDK startup status registered.
     */
    public val sdkStartupStatus: String?

    /**
     * If the sdk is disabled
     */
    public var sdkDisabled: Boolean

    /**
     * If the user is payer
     */
    public var userPayer: Boolean

    /**
     * User unique identifier
     */
    public var userIdentifier: String?

    /**
     * User email address
     */
    public var userEmailAddress: String?

    /**
     * Personas for the user
     */
    public var userPersonas: Set<String>?

    /**
     * All permanent session properties
     */
    public var permanentSessionProperties: Map<String, String>?

    /**
     * No longer used, will be removed in a future version.
     *
     * Method is still present to ensure that during any upgrades to SDK3, any custom
     * personas are merged with the user personas list.
     *
     * @return custom personas
     */
    @Deprecated("")
    public val customPersonas: Set<String>?

    /**
     * Username for the user
     */
    public var username: String?

    /**
     * The last time config was fetched from the server
     */
    public var lastConfigFetchDate: Long?

    /**
     * If the user message needs to retry send
     */
    public var userMessageNeedsRetry: Boolean

    /**
     * Increments and returns the session number ordinal. This is an integer that increments
     * at the start of every session. This allows us to check the % of sessions that didn't get
     * delivered to the backend.
     */
    public fun incrementAndGetSessionNumber(): Int

    /**
     * Increments and returns the background activity number ordinal. This is an integer that
     * increments at the start of every background activity. This allows us to check the % of
     * requests that didn't get delivered to the backend.
     */
    public fun incrementAndGetBackgroundActivityNumber(): Int

    /**
     * Increments and returns the crash number ordinal. This is an integer that
     * increments on every crash. It allows us to check the % of crashes that
     * didn't get delivered to the backend.
     */
    public fun incrementAndGetCrashNumber(): Int

    /**
     * Increments and returns the native crash number ordinal. This is an integer that
     * increments on every native crash. It allows us to check the % of native crashes that
     * didn't get delivered to the backend.
     */
    public fun incrementAndGetNativeCrashNumber(): Int

    /**
     * Last javaScript bundle string url.
     */
    public var javaScriptBundleURL: String?

    /**
     * Last javaScript bundle ID.
     */
    public var javaScriptBundleId: String?

    /**
     * Embrace sdk version.
     */
    public var rnSdkVersion: String?

    /**
     * Last javaScript patch string number.
     */
    public var javaScriptPatchNumber: String?

    /**
     * Last react native version.
     */
    public var reactNativeVersionNumber: String?

    /**
     * Last Unity version.
     */
    public var unityVersionNumber: String?

    /**
     * Last Unity Build ID
     */
    public var unityBuildIdNumber: String?

    /**
     * Last Unity SDK version
     */
    public var unitySdkVersionNumber: String?

    /**
     * Last Flutter SDK version
     */
    public var embraceFlutterSdkVersion: String?

    /**
     * Last Dart SDK version
     */
    public var dartSdkVersion: String?

    /**
     * If the device is a rooted device.
     */
    public var jailbroken: Boolean?

    /**
     * The device's screen resolution.
     */
    public var screenResolution: String?

    /**
     * The device's cpu name.
     */
    public var cpuName: String?

    /**
     * The device's egl.
     */
    public var egl: String?

    /**
     * If background activity capture is enabled
     */
    public var backgroundActivityEnabled: Boolean

    /**
     * Set of hashcodes derived from ApplicationExitInfo objects
     */
    public var applicationExitInfoHistory: Set<String>?

    /**
     * Whether or not the app was installed within the last 24 hours.
     *
     * @return true if it is the user's first day, false otherwise
     */
    public fun isUsersFirstDay(): Boolean

    /**
     * Ssuffix to compose the key to get the stored value
     */
    public fun isNetworkCaptureRuleOver(id: String): Boolean

    /**
     * Suffix to compose the key to get the stored value
     */
    public fun decreaseNetworkCaptureRuleRemainingCount(id: String, maxCount: Int)

    public companion object {
        public const val DAY_IN_MS: Long = 60 * 60 * 24 * 1000L
    }
}
