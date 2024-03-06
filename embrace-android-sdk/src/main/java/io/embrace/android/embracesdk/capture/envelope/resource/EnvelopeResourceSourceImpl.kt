package io.embrace.android.embracesdk.capture.envelope.resource

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.DeviceInfo

internal class EnvelopeResourceSourceImpl(
    private val deviceInfo: DeviceInfo,
    private val appInfo: AppInfo,
    private val metadataService: MetadataService,
) : EnvelopeResourceSource {
    override fun getEnvelopeResource(): EnvelopeResource {
        return EnvelopeResource(
            appVersion = appInfo.appVersion,
            appEcosystemId = metadataService.getPackageName(),
            appFramework = mapFramework(metadataService.getAppFramework()),
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
            // networkLogBody = ,
            // networkEncryptedLogBody = ,
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

    private fun mapFramework(appFramework: Embrace.AppFramework): EnvelopeResource.AppFramework {
        return when (appFramework) {
            Embrace.AppFramework.NATIVE ->
                EnvelopeResource.AppFramework.NATIVE

            Embrace.AppFramework.REACT_NATIVE ->
                EnvelopeResource.AppFramework.REACT_NATIVE

            Embrace.AppFramework.UNITY ->
                EnvelopeResource.AppFramework.UNITY

            Embrace.AppFramework.FLUTTER ->
                EnvelopeResource.AppFramework.FLUTTER
        }
    }
}
