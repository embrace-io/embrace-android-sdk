package io.embrace.android.embracesdk.capture.envelope.resource

import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

internal class EnvelopeResourceSourceImpl(
    private val metadataService: MetadataService
) : EnvelopeResourceSource {

    override fun getEnvelopeResource(): EnvelopeResource {
        val appInfo = metadataService.getAppInfo()
        val device = metadataService.getDeviceInfo()
        return EnvelopeResource(
            appVersion = appInfo.appVersion,
            appEcosystemId = "", //packageInfo.packageName,
            appFramework = mapFramework(metadataService.getAppFramework()),
            buildId = appInfo.buildId,
            buildType = appInfo.buildType,
            buildFlavor = appInfo.buildFlavor,
            environment = appInfo.environment,
            bundleVersion = appInfo.bundleVersion,
            sdkVersion = appInfo.sdkVersion,
            sdkSimpleVersion = appInfo.sdkSimpleVersion!!.toInt(),
            reactNativeBundleId = appInfo.reactNativeBundleId,
            javascriptPatchNumber = appInfo.javaScriptPatchNumber,
            hostedPlatformVersion = appInfo.hostedPlatformVersion,
            hostedSdkVersion = appInfo.hostedSdkVersion,
            unityBuildId = appInfo.buildGuid,
            deviceManufacturer = device.manufacturer,
            deviceModel = device.model,
            deviceArchitecture = device.architecture,
            jailbroken = device.jailbroken,
            diskTotalCapacity = device.internalStorageTotalCapacity,
            osType = device.operatingSystemType,
            osVersion = device.operatingSystemVersion,
            osCode = device.operatingSystemVersionCode.toString(),
            screenResolution = device.screenResolution,
            numCores = device.cores,
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
