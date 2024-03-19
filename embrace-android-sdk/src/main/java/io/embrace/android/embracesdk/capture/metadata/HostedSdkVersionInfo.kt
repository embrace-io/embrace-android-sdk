package io.embrace.android.embracesdk.capture.metadata

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService
import java.util.concurrent.Future

internal class HostedSdkVersionInfo(
    private val preferencesService: PreferencesService,
    private val logger: InternalEmbraceLogger,
    appFramework: Embrace.AppFramework = Embrace.AppFramework.NATIVE
) {
    private var hostedPlatformStrategy: HostedPlatformStrategy

    // precompute on initialization
    init {
        when (appFramework) {
            Embrace.AppFramework.REACT_NATIVE -> this.hostedPlatformStrategy = ReactNativePlatformStrategy()
            Embrace.AppFramework.UNITY -> this.hostedPlatformStrategy = UnityPlatformStrategy()
            Embrace.AppFramework.FLUTTER -> this.hostedPlatformStrategy = FlutterPlatformStrategy()
            else -> this.hostedPlatformStrategy = NativePlatformStrategy()
        }
    }
    var hostedSdkVersion: String? = null
        get() = field ?: hostedPlatformStrategy.getHostedSdkVersionFromPreferences(preferencesService)
        set(value) {
            val sdkVersion = hostedPlatformStrategy.getHostedSdkVersionFromPreferences(preferencesService)
            if (sdkVersion != null) {
                logger.logDeveloper("Embrace", "hostedSdkVersion is present")
                if (value != sdkVersion) {
                    logger.logDeveloper("Embrace", "Setting a new hostedSdkVersion")
                    field = value
                    hostedPlatformStrategy.setHostedSdkVersionInPreferences(value, preferencesService)
                }
            } else {
                logger.logDeveloper("Embrace", "Setting hostedSdkVersion")
                field = value
                hostedPlatformStrategy.setHostedSdkVersionInPreferences(value, preferencesService)
            }
        }

    var hostedPlatformVersion: String? = null
        get() = field ?: hostedPlatformStrategy.getHostedPlatformVersionFromPreferences(preferencesService)
        set(value) {
            val platformVersion = hostedPlatformStrategy.getHostedPlatformVersionFromPreferences(preferencesService)
            if (platformVersion != null) {
                logger.logDeveloper("Embrace", "hostedPlatformVersion is present")
                if (value != platformVersion) {
                    logger.logDeveloper("Embrace", "Setting a new hostedPlatformVersion")
                    field = value
                    hostedPlatformStrategy.setHostedPlatformVersionInPreferences(value, preferencesService)
                }
            } else {
                logger.logDeveloper("Embrace", "Setting hostedPlatformVersion")
                field = value
                hostedPlatformStrategy.setHostedPlatformVersionInPreferences(value, preferencesService)
            }
        }

    var unityBuildIdNumber: String? = null
        get() = field ?: hostedPlatformStrategy.getUnityBuildIdNumber(preferencesService)
        set(value) {
            val unityBuildIdNumber = hostedPlatformStrategy.getUnityBuildIdNumber(preferencesService)
            if (unityBuildIdNumber != null) {
                logger.logDeveloper("Embrace", "unityBuildIdNumber is present")
                if (value != unityBuildIdNumber) {
                    logger.logDeveloper("Embrace", "Setting a new unityBuildIdNumber")
                    field = value
                    hostedPlatformStrategy.setUnityBuildIdNumberInPreferences(value, preferencesService)
                }
            } else {
                logger.logDeveloper("Embrace", "Setting unityBuildIdNumber")
                field = value
                hostedPlatformStrategy.setUnityBuildIdNumberInPreferences(value, preferencesService)
            }
        }

    var javaScriptPatchNumber: String? = null
        get() = field ?: hostedPlatformStrategy.getJavaScriptPatchNumber(preferencesService)
        set(value) {
            val javaScriptPatchNumber = hostedPlatformStrategy.getJavaScriptPatchNumber(preferencesService)
            if (javaScriptPatchNumber != null) {
                logger.logDeveloper("Embrace", "javaScriptPatchNumber is present")
                if (value != javaScriptPatchNumber) {
                    logger.logDeveloper("Embrace", "Setting a new javaScriptPatchNumber")
                    field = value
                    hostedPlatformStrategy.setJavaScriptPatchNumberInPreferences(value, preferencesService)
                }
            } else {
                logger.logDeveloper("Embrace", "Setting javaScriptPatchNumber")
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
