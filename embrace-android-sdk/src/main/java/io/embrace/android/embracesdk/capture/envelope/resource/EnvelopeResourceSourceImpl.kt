package io.embrace.android.embracesdk.capture.envelope.resource

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.injection.isDebug
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

internal class EnvelopeResourceSourceImpl(
    private val hosted: HostedSdkInfo,
    private val applicationInfo: ApplicationInfo,
    private val buildInfo: BuildInfo,
    private val packageInfo: PackageInfo,
    private val appFramework: AppFramework,
    private val deviceArchitecture: DeviceArchitecture,
    private val device: Device
) : EnvelopeResourceSource {


    override fun getEnvelopeResource(): EnvelopeResource {
        return EnvelopeResource(
            appVersion = packageInfo.versionName.toString().trim { it <= ' ' },
            bundleVersion = packageInfo.versionCode.toString(),
            appEcosystemId = packageInfo.packageName,
            appFramework = mapFramework(appFramework),
            buildId = buildInfo.buildId,
            buildType = buildInfo.buildType,
            buildFlavor = buildInfo.buildFlavor,
            environment = if (applicationInfo.isDebug()) ENVIRONMENT_DEV else ENVIRONMENT_PROD,
            sdkVersion = BuildConfig.VERSION_NAME,
            sdkSimpleVersion = BuildConfig.VERSION_CODE.toIntOrNull(),
            hostedPlatformVersion = hosted.hostedPlatformVersion,
            hostedSdkVersion = hosted.hostedSdkVersion,
            reactNativeBundleId = hosted.reactNativeBundleId,
            javascriptPatchNumber = hosted.javaScriptPatchNumber,
            unityBuildId = hosted.unityBuildIdNumber,
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

    companion object {
        const val ENVIRONMENT_DEV = "dev"
        const val ENVIRONMENT_PROD = "prod"
    }
}
