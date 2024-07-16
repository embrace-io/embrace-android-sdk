package io.embrace.android.embracesdk.internal.capture.metadata

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal class HostedSdkVersionInfo(
    private val preferencesService: PreferencesService,
    appFramework: AppFramework = AppFramework.NATIVE
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

// Temporary until we confirm we can use just one preference for sdk version
internal interface HostedPlatformStrategy {
    fun getHostedSdkVersionFromPreferences(preferencesService: PreferencesService): String?
    fun setHostedSdkVersionInPreferences(
        version: String?,
        preferencesService: PreferencesService
    )
    fun getHostedPlatformVersionFromPreferences(preferencesService: PreferencesService): String?
    fun setHostedPlatformVersionInPreferences(
        value: String?,
        preferencesService: PreferencesService
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
        preferencesService: PreferencesService
    ) = Unit
}

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
        preferencesService: PreferencesService
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
        preferencesService: PreferencesService
    ) {
        preferencesService.reactNativeVersionNumber = value
    }

    override fun getJavaScriptPatchNumber(preferencesService: PreferencesService): String? {
        return preferencesService.javaScriptPatchNumber
    }

    override fun setJavaScriptPatchNumberInPreferences(
        value: String?,
        preferencesService: PreferencesService
    ) {
        preferencesService.javaScriptPatchNumber = value
    }
}

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
        preferencesService: PreferencesService
    ) = Unit
}
