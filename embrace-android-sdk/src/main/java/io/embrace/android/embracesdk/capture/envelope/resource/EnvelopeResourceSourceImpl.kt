package io.embrace.android.embracesdk.capture.envelope.resource

import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.DeviceInfo

internal class EnvelopeResourceSourceImpl(
    private val deviceInfo: DeviceInfo,
    private val appInfo: AppInfo,
    private val packageName: String,
    private val appFramework: AppFramework
) : EnvelopeResourceSource {
    override fun getEnvelopeResource(): EnvelopeResource {
        return EnvelopeResource(
            appVersion = appInfo.appVersion,
            appEcosystemId = packageName,
            appFramework = mapFramework(appFramework),
            buildId = appInfo.buildId,
            buildType = appInfo.buildType,
            buildFlavor = appInfo.buildFlavor,
            environment = appInfo.environment,
            bundleVersion = appInfo.bundleVersion,
            sdkVersion = appInfo.sdkVersion,
            sdkSimpleVersion = appInfo.sdkSimpleVersion!!.toInt(),
            reactNativeBundleId = appInfo.reactNativeBundleId,
            reactNativeVersion = appInfo.reactNativeVersion,
            javascriptPatchNumber = appInfo.javaScriptPatchNumber,
            hostedPlatformVersion = appInfo.hostedPlatformVersion,
            hostedSdkVersion = appInfo.hostedSdkVersion,
            unityBuildId = appInfo.buildGuid,
            deviceManufacturer = deviceInfo.manufacturer,
            deviceModel = deviceInfo.model,
            deviceArchitecture = deviceInfo.architecture,
            jailbroken = deviceInfo.jailbroken,
            diskTotalCapacity = deviceInfo.internalStorageTotalCapacity,
            osType = deviceInfo.operatingSystemType,
            osVersion = deviceInfo.operatingSystemVersion,
            osCode = deviceInfo.operatingSystemVersionCode.toString(),
            screenResolution = deviceInfo.screenResolution,
            numCores = deviceInfo.cores,
        )
    }

    private fun mapFramework(appFramework: AppFramework): EnvelopeResource.AppFramework {
        return when (appFramework) {
            AppFramework.NATIVE ->
                EnvelopeResource.AppFramework.NATIVE

            AppFramework.REACT_NATIVE ->
                EnvelopeResource.AppFramework.REACT_NATIVE

            AppFramework.UNITY ->
                EnvelopeResource.AppFramework.UNITY

            AppFramework.FLUTTER ->
                EnvelopeResource.AppFramework.FLUTTER
        }
    }
}
