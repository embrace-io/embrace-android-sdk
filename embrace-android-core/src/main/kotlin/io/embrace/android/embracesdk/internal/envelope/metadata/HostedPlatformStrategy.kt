package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

// Temporary until we confirm we can use just one preference for sdk version
internal interface HostedPlatformStrategy {
    fun getHostedSdkVersionFromPreferences(preferencesService: PreferencesService): String?
    fun setHostedSdkVersionInPreferences(
        version: String?,
        preferencesService: PreferencesService,
    )

    fun getHostedPlatformVersionFromPreferences(preferencesService: PreferencesService): String?
    fun setHostedPlatformVersionInPreferences(
        value: String?,
        preferencesService: PreferencesService,
    )

    fun getUnityBuildIdNumber(preferencesService: PreferencesService): String? {
        return null
    }

    fun setUnityBuildIdNumberInPreferences(value: String?, preferencesService: PreferencesService) = Unit
    fun getJavaScriptPatchNumber(preferencesService: PreferencesService): String? {
        return null
    }

    fun setJavaScriptPatchNumberInPreferences(
        value: String?,
        preferencesService: PreferencesService,
    ) = Unit
}
