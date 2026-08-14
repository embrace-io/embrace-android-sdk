package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * Maps an [EnvelopeResource] to its protobuf equivalent.
 */
internal fun EnvelopeResource.toProto(): EnvelopeResourceProto = EnvelopeResourceProto(
    app_version = appVersion,
    app_framework = appFramework?.toProto(),
    build_id = buildId,
    app_ecosystem_id = appEcosystemId,
    build_type = buildType,
    build_flavor = buildFlavor,
    environment = environment,
    bundle_version = bundleVersion,
    sdk_version = sdkVersion,
    sdk_simple_version = sdkSimpleVersion,
    react_native_bundle_id = reactNativeBundleId,
    react_native_version = reactNativeVersion,
    javascript_patch_number = javascriptPatchNumber,
    hosted_platform_version = hostedPlatformVersion,
    hosted_sdk_version = hostedSdkVersion,
    unity_build_id = unityBuildId,
    device_manufacturer = deviceManufacturer,
    device_model = deviceModel,
    device_architecture = deviceArchitecture,
    jailbroken = jailbroken,
    disk_total_capacity = diskTotalCapacity,
    os_type = osType,
    os_name = osName,
    os_version = osVersion,
    os_code = osCode,
    screen_resolution = screenResolution,
    num_cores = numCores,
    uses_emmc_storage = usesEmmcStorage,
    device_soc_model = deviceSocModel,
    extras = extras,
)

internal fun AppFramework.toProto(): EnvelopeResourceProto.AppFramework = when (this) {
    AppFramework.NATIVE -> EnvelopeResourceProto.AppFramework.NATIVE
    AppFramework.REACT_NATIVE -> EnvelopeResourceProto.AppFramework.REACT_NATIVE
    AppFramework.UNITY -> EnvelopeResourceProto.AppFramework.UNITY
    AppFramework.FLUTTER -> EnvelopeResourceProto.AppFramework.FLUTTER
}
