package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

class FlutterSdkVersionInfo(
    private val prefs: PreferencesService,
) : HostedSdkVersionInfo {

    override var hostedSdkVersion: String?
        get() = prefs.embraceFlutterSdkVersion
        set(value) {
            prefs.embraceFlutterSdkVersion = value
        }

    override var hostedPlatformVersion: String?
        get() = prefs.dartSdkVersion
        set(value) {
            prefs.dartSdkVersion = value
        }
}
