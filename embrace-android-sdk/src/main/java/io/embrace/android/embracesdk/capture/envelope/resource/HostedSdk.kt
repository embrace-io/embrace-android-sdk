package io.embrace.android.embracesdk.capture.envelope.resource

import io.embrace.android.embracesdk.prefs.PreferencesService

internal interface HostedSdkInfo {
    val hostedSdkVersion: String?
    val hostedPlatformVersion: String?
    val unityBuildIdNumber: String?
    val reactNativeBundleId: String?
    val javaScriptPatchNumber: String?
}

internal class UnitySdkInfo(
    private val preferencesService: PreferencesService
) : HostedSdkInfo {
    override val hostedSdkVersion: String?
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
    override val hostedSdkVersion: String?
        get() = preferencesService.rnSdkVersion
    override val hostedPlatformVersion: String?
        get() = preferencesService.reactNativeVersionNumber
    override val unityBuildIdNumber: String? = null
    override val reactNativeBundleId: String? = preferencesService.javaScriptBundleId
    override val javaScriptPatchNumber: String?
        get() = preferencesService.javaScriptPatchNumber
}
