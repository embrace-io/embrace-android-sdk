package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.buildinfo.BuildInfo
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.injection.PackageVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

internal class EnvelopeResourceSourceImpl(
    private val hosted: HostedSdkVersionInfo,
    private val environment: AppEnvironment.Environment,
    private val buildInfo: BuildInfo,
    private val packageVersionInfo: PackageVersionInfo,
    private val appFramework: AppFramework,
    private val deviceArchitecture: DeviceArchitecture,
    private val device: Device,
    private val rnBundleIdTracker: RnBundleIdTracker,
) : EnvelopeResourceSource {

    override fun getEnvelopeResource(): EnvelopeResource {
        return EnvelopeResource(
            appVersion = packageVersionInfo.versionName,
            bundleVersion = packageVersionInfo.versionCode,
            appFramework = appFramework,
            buildId = buildInfo.buildId,
            buildType = buildInfo.buildType,
            buildFlavor = buildInfo.buildFlavor,
            environment = environment.value,
            sdkVersion = BuildConfig.VERSION_NAME,
            sdkSimpleVersion = BuildConfig.VERSION_CODE.toIntOrNull(),
            hostedPlatformVersion = hosted.hostedPlatformVersion,
            hostedSdkVersion = hosted.hostedSdkVersion,
            reactNativeBundleId = rnBundleIdTracker.getReactNativeBundleId(),
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
}
