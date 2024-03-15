package io.embrace.android.embracesdk.capture.envelope.resource

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService

internal interface HostedSdkInfo {
    var hostedSdkVersion: String?
    val hostedPlatformVersion: String?
    val unityBuildIdNumber: String?
    val reactNativeBundleId: String?
    val javaScriptPatchNumber: String?
}

internal class UnitySdkInfo(
    private val preferencesService: PreferencesService
) : HostedSdkInfo {
    override var hostedSdkVersion: String? = null
        get() = preferencesService.unitySdkVersionNumber
    override val hostedPlatformVersion: String?
        get() = preferencesService.unityVersionNumber
    override val unityBuildIdNumber: String?
        get() = preferencesService.unityBuildIdNumber
    override val reactNativeBundleId: String? = null
    override val javaScriptPatchNumber: String? = null
}

internal class ReactNativeSdkInfo(
    private val preferencesService: PreferencesService
) : HostedSdkInfo {
    override var hostedSdkVersion: String? = null
        get() = preferencesService.rnSdkVersion
    override val hostedPlatformVersion: String?
        get() = preferencesService.reactNativeVersionNumber
    override val unityBuildIdNumber: String? = null
    override val reactNativeBundleId: String? = preferencesService.javaScriptBundleId
    override val javaScriptPatchNumber: String?
        get() = preferencesService.javaScriptPatchNumber
}

internal class FlutterSdkInfo(
    private val preferencesService: PreferencesService,
    private val logger: InternalEmbraceLogger
) : HostedSdkInfo {
    override var hostedSdkVersion: String? = null
        get() = field ?: preferencesService.embraceFlutterSdkVersion
        set(value) {
            val embraceFlutterSdkVersion = preferencesService.embraceFlutterSdkVersion
            if (embraceFlutterSdkVersion != null) {
                logger.logDeveloper("Embrace", "hostedSdkVersion is present")
                if (value != embraceFlutterSdkVersion) {
                    logger.logDeveloper("Embrace", "Setting a new hostedSdkVersion")
                    field = value
                    preferencesService.embraceFlutterSdkVersion = value
                }
            } else {
                logger.logDeveloper("Embrace", "Setting hostedSdkVersion")
                field = value
                preferencesService.embraceFlutterSdkVersion = value
            }
        }

    override var hostedPlatformVersion: String? = null
        get() = field ?: preferencesService.dartSdkVersion
        set(value) {
            field = value
            preferencesService.dartSdkVersion = value
        }

    override val unityBuildIdNumber: String? = null
    override val reactNativeBundleId: String? = null
    override val javaScriptPatchNumber: String? = null
}
