package io.embrace.android.embracesdk.capture.envelope.resource

import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

internal class EnvelopeResourceSourceImpl(
    private val hosted: HostedSdkVersionInfo,
    private val environment: AppEnvironment.Environment,
    private val buildInfo: BuildInfo,
    private val packageInfo: PackageInfo,
    private val appFramework: AppFramework,
    private val deviceArchitecture: DeviceArchitecture,
    private val device: Device,
    private val metadataService: MetadataService
) : EnvelopeResourceSource {

    @Suppress("DEPRECATION")
    override fun getEnvelopeResource(): EnvelopeResource {
        return EnvelopeResource(
            appVersion = packageInfo.versionName?.toString()?.trim { it <= ' ' } ?: "",
            bundleVersion = packageInfo.versionCode.toString(),
            appEcosystemId = packageInfo.packageName,
            appFramework = mapFramework(appFramework),
            buildId = buildInfo.buildId,
            buildType = buildInfo.buildType,
            buildFlavor = buildInfo.buildFlavor,
            environment = environment.value,
            sdkVersion = BuildConfig.VERSION_NAME,
            sdkSimpleVersion = BuildConfig.VERSION_CODE.toIntOrNull(),
            hostedPlatformVersion = hosted.hostedPlatformVersion,
            hostedSdkVersion = hosted.hostedSdkVersion,
            reactNativeBundleId = metadataService.getReactNativeBundleId(),
            javascriptPatchNumber = hosted.javaScriptPatchNumber,
            unityBuildId = hosted.unityBuildIdNumber,
            deviceManufacturer = device.systemInfo.deviceManufacturer,
            deviceModel = device.systemInfo.deviceModel,
            deviceArchitecture = deviceArchitecture.architecture,
            jailbroken = device.isJailbroken,
            diskTotalCapacity = device.internalStorageTotalCapacity.value,
            osType = device.systemInfo.osType,
            osName = device.systemInfo.osName,
            osVersion = device.systemInfo.osVersion,
            osCode = device.systemInfo.androidOsApiLevel,
            screenResolution = device.screenResolution,
            numCores = device.numberOfCores
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
