package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.concurrent.ConcurrentHashMap

class EnvelopeResourceSourceImpl(
    private val configService: ConfigService,
    private val hosted: HostedSdkVersionInfo,
    private val environment: AppEnvironment.Environment,
    private val device: Device,
    private val versionName: String,
    private val versionCode: Int?,
    private val rnBundleIdProvider: () -> String?,
    private val otelResourceAttributesSupplier: Provider<Map<String, String>>,
) : EnvelopeResourceSource {

    private val extras = ConcurrentHashMap<String, String>()

    override fun getEnvelopeResource(): EnvelopeResource {
        val buildInfo = configService.buildInfo

        return EnvelopeResource(
            appVersion = buildInfo.versionName,
            bundleVersion = buildInfo.versionCode,
            appEcosystemId = buildInfo.packageName,
            appFramework = configService.appFramework,
            buildId = buildInfo.buildId,
            buildType = buildInfo.buildType,
            buildFlavor = buildInfo.buildFlavor,
            environment = environment.value,
            sdkVersion = versionName,
            sdkSimpleVersion = versionCode,
            hostedPlatformVersion = hosted.hostedPlatformVersion,
            hostedSdkVersion = hosted.hostedSdkVersion,
            reactNativeBundleId = rnBundleIdProvider(),
            javascriptPatchNumber = hosted.javaScriptPatchNumber,
            unityBuildId = hosted.unityBuildIdNumber,
            deviceManufacturer = device.systemInfo.deviceManufacturer,
            deviceModel = device.systemInfo.deviceModel,
            deviceArchitecture = configService.cpuAbi.archName,
            jailbroken = device.isJailbroken,
            diskTotalCapacity = device.internalStorageTotalCapacity.value,
            osType = device.systemInfo.osType,
            osName = device.systemInfo.osName,
            osVersion = device.systemInfo.osVersion,
            osCode = device.systemInfo.androidOsApiLevel,
            screenResolution = device.screenResolution,
            numCores = device.numberOfCores,
            extras = extras.toMap() + otelResourceAttributesSupplier()
        )
    }

    override fun add(key: String, value: String) {
        extras[key] = value
    }
}
