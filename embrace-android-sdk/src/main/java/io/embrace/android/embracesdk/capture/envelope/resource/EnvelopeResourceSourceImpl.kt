package io.embrace.android.embracesdk.capture.envelope.resource

import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.payload.AppInfo

internal class EnvelopeResourceSourceImpl(
    private val appInfo: AppInfo,
    private val buildInfo: BuildInfo,
    private val packageInfo: PackageInfo,
    private val appFramework: AppFramework,
    private val deviceArchitecture: DeviceArchitecture,
    private val device: Device
) : EnvelopeResourceSource {

    override fun getEnvelopeResource(): EnvelopeResource {
        return EnvelopeResource(
            appVersion = appInfo.appVersion,
            appEcosystemId = packageInfo.packageName,
            appFramework = mapFramework(appFramework),
            buildId = buildInfo.buildId,
            buildType = buildInfo.buildType,
            buildFlavor = buildInfo.buildFlavor,
            environment = appInfo.environment,
            bundleVersion = appInfo.bundleVersion,
            sdkVersion = BuildConfig.VERSION_NAME,
            sdkSimpleVersion = BuildConfig.VERSION_CODE.toInt(),
            reactNativeBundleId = appInfo.reactNativeBundleId,
            javascriptPatchNumber = appInfo.javaScriptPatchNumber,
            hostedPlatformVersion = appInfo.hostedPlatformVersion,
            hostedSdkVersion = appInfo.hostedSdkVersion,
            unityBuildId = appInfo.buildGuid,
            deviceManufacturer = device.manufacturer,
            deviceModel = device.model,
            deviceArchitecture = deviceArchitecture.architecture,
            jailbroken = device.isJailbroken,
            diskTotalCapacity = device.internalStorageTotalCapacity.value,
            osType = device.operatingSystemType,
            osVersion = device.operatingSystemVersion,
            osCode = device.operatingSystemVersionCode.toString(),
            screenResolution = device.screenResolution,
            numCores = device.numberOfCores,
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
