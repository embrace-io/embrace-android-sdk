package io.embrace.android.embracesdk.capture.metadata

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService

internal open class HostedSdkVersionInfo(
    private val preferencesService: PreferencesService,
    private val logger: InternalEmbraceLogger
) {
    var hostedSdkVersion: String? = null
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

    var hostedPlatformVersion: String? = null
        get() = field ?: preferencesService.dartSdkVersion
        set(value) {
            val dartSdkVersion = preferencesService.dartSdkVersion
            if (dartSdkVersion != null) {
                logger.logDeveloper("Embrace", "hostedPlatformVersion is present")
                if (value != dartSdkVersion) {
                    logger.logDeveloper("Embrace", "Setting a new hostedPlatformVersion")
                    field = value
                    preferencesService.dartSdkVersion = value
                }
            } else {
                logger.logDeveloper("Embrace", "Setting hostedPlatformVersion")
                field = value
                preferencesService.dartSdkVersion = value
            }
        }
}
//
//internal class UnitySdkInfo(
//    private val preferencesService: PreferencesService
//) : HostedSdkVersionInfo {
//    override var hostedSdkVersion: String? = null
//        get() = preferencesService.unitySdkVersionNumber
//    override val hostedPlatformVersion: String?
//        get() = preferencesService.unityVersionNumber
//    override val unityBuildIdNumber: String?
//        get() = preferencesService.unityBuildIdNumber
//    override val reactNativeBundleId: String? = null
//    override val javaScriptPatchNumber: String? = null
//}
//
//internal class ReactNativeSdkInfo(
//    private val preferencesService: PreferencesService
//) : HostedSdkVersionInfo {
//    override var hostedSdkVersion: String? = null
//        get() = preferencesService.rnSdkVersion
//    override val hostedPlatformVersion: String?
//        get() = preferencesService.reactNativeVersionNumber
//    override val unityBuildIdNumber: String? = null
//    override val reactNativeBundleId: String? = preferencesService.javaScriptBundleId
//    override val javaScriptPatchNumber: String?
//        get() = preferencesService.javaScriptPatchNumber
//}
