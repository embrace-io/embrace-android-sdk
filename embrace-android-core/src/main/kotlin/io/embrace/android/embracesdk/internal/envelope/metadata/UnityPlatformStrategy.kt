package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal class UnityPlatformStrategy : HostedPlatformStrategy {
    override fun getHostedSdkVersionFromPreferences(preferencesService: PreferencesService): String? {
        return preferencesService.unitySdkVersionNumber
    }

    override fun setHostedSdkVersionInPreferences(version: String?, preferencesService: PreferencesService) {
        preferencesService.unitySdkVersionNumber = version
    }

    override fun getHostedPlatformVersionFromPreferences(preferencesService: PreferencesService): String? {
        return preferencesService.unityVersionNumber
    }

    override fun setHostedPlatformVersionInPreferences(
        value: String?,
        preferencesService: PreferencesService,
    ) {
        preferencesService.unityVersionNumber = value
    }

    override fun getUnityBuildIdNumber(preferencesService: PreferencesService): String? {
        return preferencesService.unityBuildIdNumber
    }

    override fun setUnityBuildIdNumberInPreferences(value: String?, preferencesService: PreferencesService) {
        preferencesService.unityBuildIdNumber = value
    }
}
