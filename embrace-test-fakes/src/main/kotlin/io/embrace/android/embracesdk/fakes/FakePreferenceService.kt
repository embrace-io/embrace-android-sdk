package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

@Suppress("LongParameterList")
class FakePreferenceService(
    override var appVersion: String? = null,
    override var osVersion: String? = null,
    override var installDate: Long? = 0,
    override var deviceIdentifier: String = "",
    override var sdkDisabled: Boolean = false,
    override var userPayer: Boolean = false,
    override var userIdentifier: String? = null,
    override var userEmailAddress: String? = null,
    override var userPersonas: Set<String>? = null,
    override var username: String? = null,
    override var permanentSessionProperties: Map<String, String>? = null,
    @Deprecated("") override var customPersonas: Set<String>? = null,
    override var lastConfigFetchDate: Long? = null,
    override var userMessageNeedsRetry: Boolean = false,
    override var reactNativeVersionNumber: String? = null,
    override var unityVersionNumber: String? = null,
    override var unityBuildIdNumber: String? = null,
    override var unitySdkVersionNumber: String? = null,
    override var screenResolution: String? = null,
    override var backgroundActivityEnabled: Boolean = false,
    override var dartSdkVersion: String? = null,
    override var javaScriptBundleURL: String? = null,
    override var javaScriptBundleId: String? = null,
    override var rnSdkVersion: String? = null,
    override var javaScriptPatchNumber: String? = null,
    override var embraceFlutterSdkVersion: String? = null,
    override var jailbroken: Boolean? = null,
    override var applicationExitInfoHistory: Set<String>? = null,
    override var cpuName: String? = null,
    override var egl: String? = null,
    val sessionNumber: () -> Int = { 0 },
    val bgActivityNumber: () -> Int = { 5 }
) : PreferencesService {

    var networkCaptureRuleOver: Boolean = false
    var firstDay: Boolean = false
    var incrementAndGetSessionNumberCount: Int = 0

    override fun isNetworkCaptureRuleOver(id: String): Boolean {
        return networkCaptureRuleOver
    }

    override fun decreaseNetworkCaptureRuleRemainingCount(id: String, maxCount: Int) {
    }

    override fun incrementAndGetSessionNumber(): Int {
        incrementAndGetSessionNumberCount++
        return sessionNumber()
    }

    override fun incrementAndGetBackgroundActivityNumber(): Int = bgActivityNumber()

    override fun incrementAndGetCrashNumber(): Int {
        return 1
    }

    override fun incrementAndGetNativeCrashNumber(): Int {
        return 1
    }

    override fun isUsersFirstDay(): Boolean = firstDay
}
