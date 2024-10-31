package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal class ReactNativePlatformStrategy : HostedPlatformStrategy {
    override fun getHostedSdkVersionFromPreferences(preferencesService: PreferencesService): String? {
        return preferencesService.rnSdkVersion
    }

    override fun setHostedSdkVersionInPreferences(version: String?, preferencesService: PreferencesService) {
        preferencesService.rnSdkVersion = version
    }

    override fun getHostedPlatformVersionFromPreferences(preferencesService: PreferencesService): String? {
        return preferencesService.reactNativeVersionNumber
    }

    override fun setHostedPlatformVersionInPreferences(
        value: String?,
        preferencesService: PreferencesService,
    ) {
        preferencesService.reactNativeVersionNumber = value
    }

    override fun getJavaScriptPatchNumber(preferencesService: PreferencesService): String? {
        return preferencesService.javaScriptPatchNumber
    }

    override fun setJavaScriptPatchNumberInPreferences(
        value: String?,
        preferencesService: PreferencesService,
    ) {
        preferencesService.javaScriptPatchNumber = value
    }
}
