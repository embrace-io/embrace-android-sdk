package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

class HostedSdkVersionInfo(
    private val preferencesService: PreferencesService,
    appFramework: AppFramework = AppFramework.NATIVE,
) {
    private var hostedPlatformStrategy: HostedPlatformStrategy

    init {
        when (appFramework) {
            AppFramework.REACT_NATIVE -> this.hostedPlatformStrategy = ReactNativePlatformStrategy()
            AppFramework.UNITY -> this.hostedPlatformStrategy = UnityPlatformStrategy()
            AppFramework.FLUTTER -> this.hostedPlatformStrategy = FlutterPlatformStrategy()
            else -> this.hostedPlatformStrategy = NativePlatformStrategy()
        }
    }

    var hostedSdkVersion: String? = null
        get() = field ?: hostedPlatformStrategy.getHostedSdkVersionFromPreferences(preferencesService)
        set(value) {
            val sdkVersion = hostedPlatformStrategy.getHostedSdkVersionFromPreferences(preferencesService)
            if (sdkVersion != null) {
                if (value != sdkVersion) {
                    field = value
                    hostedPlatformStrategy.setHostedSdkVersionInPreferences(value, preferencesService)
                }
            } else {
                field = value
                hostedPlatformStrategy.setHostedSdkVersionInPreferences(value, preferencesService)
            }
        }

    var hostedPlatformVersion: String? = null
        get() = field ?: hostedPlatformStrategy.getHostedPlatformVersionFromPreferences(preferencesService)
        set(value) {
            val platformVersion = hostedPlatformStrategy.getHostedPlatformVersionFromPreferences(preferencesService)
            if (platformVersion != null) {
                if (value != platformVersion) {
                    field = value
                    hostedPlatformStrategy.setHostedPlatformVersionInPreferences(value, preferencesService)
                }
            } else {
                field = value
                hostedPlatformStrategy.setHostedPlatformVersionInPreferences(value, preferencesService)
            }
        }

    var unityBuildIdNumber: String? = null
        get() = field ?: hostedPlatformStrategy.getUnityBuildIdNumber(preferencesService)
        set(value) {
            val unityBuildIdNumber = hostedPlatformStrategy.getUnityBuildIdNumber(preferencesService)
            if (unityBuildIdNumber != null) {
                if (value != unityBuildIdNumber) {
                    field = value
                    hostedPlatformStrategy.setUnityBuildIdNumberInPreferences(value, preferencesService)
                }
            } else {
                field = value
                hostedPlatformStrategy.setUnityBuildIdNumberInPreferences(value, preferencesService)
            }
        }

    var javaScriptPatchNumber: String? = null
        get() = field ?: hostedPlatformStrategy.getJavaScriptPatchNumber(preferencesService)
        set(value) {
            val javaScriptPatchNumber = hostedPlatformStrategy.getJavaScriptPatchNumber(preferencesService)
            if (javaScriptPatchNumber != null) {
                if (value != javaScriptPatchNumber) {
                    field = value
                    hostedPlatformStrategy.setJavaScriptPatchNumberInPreferences(value, preferencesService)
                }
            } else {
                field = value
                hostedPlatformStrategy.setJavaScriptPatchNumberInPreferences(value, preferencesService)
            }
        }
}
