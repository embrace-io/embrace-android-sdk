package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

class UnitySdkVersionInfo(
    private val prefs: PreferencesService,
) : HostedSdkVersionInfo {

    override var hostedSdkVersion: String?
        get() = prefs.unitySdkVersionNumber
        set(value) {
            prefs.unitySdkVersionNumber = value
        }

    override var hostedPlatformVersion: String?
        get() = prefs.unityVersionNumber
        set(value) {
            prefs.unityVersionNumber = value
        }

    override var unityBuildIdNumber: String?
        get() = prefs.unityBuildIdNumber
        set(value) {
            prefs.unityBuildIdNumber = value
        }
}
