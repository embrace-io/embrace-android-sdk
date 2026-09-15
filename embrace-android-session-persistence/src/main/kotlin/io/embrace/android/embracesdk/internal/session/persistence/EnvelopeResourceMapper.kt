package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * Maps an [EnvelopeResource] to its protobuf equivalent.
 */
internal fun EnvelopeResource.toProto(): ResourceProto = ResourceProto(
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
    device_manufacturer = deviceManufacturer,
    device_model = deviceModel,
    device_architecture = deviceArchitecture,
    disk_total_capacity = diskTotalCapacity,
    os_type = osType,
    os_name = osName,
    os_version = osVersion,
    os_code = osCode,
    num_cores = numCores,
    device_soc_model = deviceSocModel,
    jailbroken = jailbroken,
    screen_resolution = screenResolution,
    uses_emmc_storage = usesEmmcStorage,
    hosted_platform_version = hostedPlatformVersion,
    hosted_sdk_version = hostedSdkVersion,
    javascript_patch_number = javascriptPatchNumber,
    unity_build_id = unityBuildId,
    react_native_bundle_id = reactNativeBundleId,
    react_native_version = reactNativeVersion,
    extras = extras,
)

internal fun ResourceProto.toPayload(): EnvelopeResource = EnvelopeResource(
    appVersion = app_version,
    appFramework = app_framework?.toPayload(),
    buildId = build_id,
    appEcosystemId = app_ecosystem_id,
    buildType = build_type,
    buildFlavor = build_flavor,
    environment = environment,
    bundleVersion = bundle_version,
    sdkVersion = sdk_version,
    sdkSimpleVersion = sdk_simple_version,
    reactNativeBundleId = react_native_bundle_id,
    reactNativeVersion = react_native_version,
    javascriptPatchNumber = javascript_patch_number,
    hostedPlatformVersion = hosted_platform_version,
    hostedSdkVersion = hosted_sdk_version,
    unityBuildId = unity_build_id,
    deviceManufacturer = device_manufacturer,
    deviceModel = device_model,
    deviceArchitecture = device_architecture,
    jailbroken = jailbroken,
    diskTotalCapacity = disk_total_capacity,
    osType = os_type,
    osName = os_name,
    osVersion = os_version,
    osCode = os_code,
    screenResolution = screen_resolution,
    numCores = num_cores,
    usesEmmcStorage = uses_emmc_storage,
    deviceSocModel = device_soc_model,
    extras = extras,
)

internal fun AppFramework.toProto(): ResourceProto.AppFramework = when (this) {
    AppFramework.NATIVE -> ResourceProto.AppFramework.NATIVE
    AppFramework.REACT_NATIVE -> ResourceProto.AppFramework.REACT_NATIVE
    AppFramework.UNITY -> ResourceProto.AppFramework.UNITY
    AppFramework.FLUTTER -> ResourceProto.AppFramework.FLUTTER
}

internal fun ResourceProto.AppFramework.toPayload(): AppFramework? = when (this) {
    ResourceProto.AppFramework.UNSPECIFIED -> null
    ResourceProto.AppFramework.NATIVE -> AppFramework.NATIVE
    ResourceProto.AppFramework.REACT_NATIVE -> AppFramework.REACT_NATIVE
    ResourceProto.AppFramework.UNITY -> AppFramework.UNITY
    ResourceProto.AppFramework.FLUTTER -> AppFramework.FLUTTER
}
