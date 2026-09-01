package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * Maps an [EnvelopeResource] to its protobuf equivalent.
 */
internal fun EnvelopeResource.toImmutableProto(): ImmutableResourceProto = ImmutableResourceProto(
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
)

internal fun EnvelopeResource.toMutableProto(): MutableResourceProto = MutableResourceProto(
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

internal fun ImmutableResourceProto.toPayload(mutable: MutableResourceProto): EnvelopeResource = EnvelopeResource(
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
    reactNativeBundleId = mutable.react_native_bundle_id,
    reactNativeVersion = mutable.react_native_version,
    javascriptPatchNumber = mutable.javascript_patch_number,
    hostedPlatformVersion = mutable.hosted_platform_version,
    hostedSdkVersion = mutable.hosted_sdk_version,
    unityBuildId = mutable.unity_build_id,
    deviceManufacturer = device_manufacturer,
    deviceModel = device_model,
    deviceArchitecture = device_architecture,
    jailbroken = mutable.jailbroken,
    diskTotalCapacity = disk_total_capacity,
    osType = os_type,
    osName = os_name,
    osVersion = os_version,
    osCode = os_code,
    screenResolution = mutable.screen_resolution,
    numCores = num_cores,
    usesEmmcStorage = mutable.uses_emmc_storage,
    deviceSocModel = device_soc_model,
    extras = mutable.extras,
)

internal fun AppFramework.toProto(): ImmutableResourceProto.AppFramework = when (this) {
    AppFramework.NATIVE -> ImmutableResourceProto.AppFramework.NATIVE
    AppFramework.REACT_NATIVE -> ImmutableResourceProto.AppFramework.REACT_NATIVE
    AppFramework.UNITY -> ImmutableResourceProto.AppFramework.UNITY
    AppFramework.FLUTTER -> ImmutableResourceProto.AppFramework.FLUTTER
}

internal fun ImmutableResourceProto.AppFramework.toPayload(): AppFramework? = when (this) {
    ImmutableResourceProto.AppFramework.UNSPECIFIED -> null
    ImmutableResourceProto.AppFramework.NATIVE -> AppFramework.NATIVE
    ImmutableResourceProto.AppFramework.REACT_NATIVE -> AppFramework.REACT_NATIVE
    ImmutableResourceProto.AppFramework.UNITY -> AppFramework.UNITY
    ImmutableResourceProto.AppFramework.FLUTTER -> AppFramework.FLUTTER
}
