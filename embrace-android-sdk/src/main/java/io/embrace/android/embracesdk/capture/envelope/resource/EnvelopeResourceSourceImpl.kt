package io.embrace.android.embracesdk.capture.envelope.resource

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.injection.isDebug
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

internal class EnvelopeResourceSourceImpl(
    private val metadataService: MetadataService
) : EnvelopeResourceSource {


    override fun getEnvelopeResource(): EnvelopeResource {
        val appInfo = metadataService.getAppInfo()
        val device = metadataService.getDeviceInfo()
        return EnvelopeResource(
            appVersion = appInfo.appVersion,
            bundleVersion = appInfo.bundleVersion,
            appEcosystemId = metadataService.getPackageName(),
            appFramework = mapFramework(metadataService.getAppFramework()),
            buildId = appInfo.buildId,
            buildType = appInfo.buildType,
            buildFlavor = appInfo.buildFlavor,
            environment = appInfo.environment,
            sdkVersion = BuildConfig.VERSION_NAME,
            sdkSimpleVersion = BuildConfig.VERSION_CODE.toIntOrNull(),
            hostedPlatformVersion = appInfo.hostedPlatformVersion,
            hostedSdkVersion = appInfo.hostedSdkVersion,
            reactNativeBundleId = metadataService.getReactNativeBundleId(),
            javascriptPatchNumber = appInfo.javaScriptPatchNumber,
            unityBuildId = appInfo.buildId,
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

    companion object {
        const val ENVIRONMENT_DEV = "dev"
        const val ENVIRONMENT_PROD = "prod"
    }
}
