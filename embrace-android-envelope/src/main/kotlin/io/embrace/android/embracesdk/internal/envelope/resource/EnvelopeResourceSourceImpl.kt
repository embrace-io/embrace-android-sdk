package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.config.instrumented.schema.ProjectConfig
import io.embrace.android.embracesdk.internal.envelope.DeviceArchitecture
import io.embrace.android.embracesdk.internal.envelope.PackageVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

class EnvelopeResourceSourceImpl(
    private val hosted: HostedSdkVersionInfo,
    private val environment: AppEnvironment.Environment,
    private val projectConfig: ProjectConfig,
    private val packageVersionInfo: PackageVersionInfo,
    private val appFramework: AppFramework,
    private val deviceArchitecture: DeviceArchitecture,
    private val device: Device,
    private val rnBundleIdTracker: RnBundleIdTracker,
    private val versionName: String,
    private val versionCode: Int?,
) : EnvelopeResourceSource {

    override fun getEnvelopeResource(): EnvelopeResource {
        return EnvelopeResource(
            appVersion = packageVersionInfo.versionName,
            bundleVersion = packageVersionInfo.versionCode,
            appEcosystemId = packageVersionInfo.packageName,
            appFramework = appFramework,
            buildId = projectConfig.getBuildId(),
            buildType = projectConfig.getBuildType(),
            buildFlavor = projectConfig.getBuildFlavor(),
            environment = environment.value,
            sdkVersion = versionName,
            sdkSimpleVersion = versionCode,
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
