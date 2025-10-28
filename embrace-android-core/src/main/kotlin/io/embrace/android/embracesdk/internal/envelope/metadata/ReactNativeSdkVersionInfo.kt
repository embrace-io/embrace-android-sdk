package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

class ReactNativeSdkVersionInfo(
    private val prefs: PreferencesService,
) : HostedSdkVersionInfo {

    override var hostedSdkVersion: String?
        get() = prefs.rnSdkVersion
        set(value) {
            prefs.rnSdkVersion = value
        }

    override var hostedPlatformVersion: String?
        get() = prefs.reactNativeVersionNumber
        set(value) {
            prefs.reactNativeVersionNumber = value
        }

    override var javaScriptPatchNumber: String?
        get() = prefs.javaScriptPatchNumber
        set(value) {
            prefs.javaScriptPatchNumber = value
        }
}
