package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal class FlutterPlatformStrategy : HostedPlatformStrategy {
    override fun getHostedSdkVersionFromPreferences(preferencesService: PreferencesService): String? {
        return preferencesService.embraceFlutterSdkVersion
    }

    override fun setHostedSdkVersionInPreferences(version: String?, preferencesService: PreferencesService) {
        preferencesService.embraceFlutterSdkVersion = version
    }

    override fun getHostedPlatformVersionFromPreferences(preferencesService: PreferencesService): String? {
        return preferencesService.dartSdkVersion
    }

    override fun setHostedPlatformVersionInPreferences(
        value: String?,
        preferencesService: PreferencesService
    ) {
        preferencesService.dartSdkVersion = value
    }
}
