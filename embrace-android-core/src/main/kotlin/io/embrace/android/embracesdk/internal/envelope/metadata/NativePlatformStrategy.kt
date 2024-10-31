package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal class NativePlatformStrategy : HostedPlatformStrategy {
    override fun getHostedSdkVersionFromPreferences(preferencesService: PreferencesService): String? {
        return null
    }

    override fun setHostedSdkVersionInPreferences(version: String?, preferencesService: PreferencesService) =
        Unit

    override fun getHostedPlatformVersionFromPreferences(preferencesService: PreferencesService): String? {
        return null
    }

    override fun setHostedPlatformVersionInPreferences(
        value: String?,
        preferencesService: PreferencesService,
    ) = Unit
}
