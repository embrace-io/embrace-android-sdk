package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Immutable attributes about the app, device, and Embrace SDK internal state
 * for duration of an app launch.
 */
@JsonClass(generateAdapter = true)
internal data class EnvelopeResource(
    @Json(name = "app_version")
    val appVersion: String? = null,

    @Json(name = "app_framework")
    val appFramework: Int? = null,

    @Json(name = "build_id")
    val buildId: String? = null,

    @Json(name = "build_type")
    val buildType: String? = null,

    @Json(name = "build_flavor")
    val buildFlavor: String? = null,

    @Json(name = "environment")
    val environment: String? = null,

    @Json(name = "bundle_version")
    val bundleVersion: String? = null,

    @Json(name = "sdk_version")
    val sdkVersion: String? = null,

    @Json(name = "sdk_simple_version")
    val sdkSimpleVersion: String? = null,

    @Json(name = "react_native_bundle_id")
    val reactNativeBundleId: String? = null,

    @Json(name = "react_native_version")
    val reactNativeVersion: String? = null,

    @Json(name = "javascript_patch_number")
    val javascriptPatchNumber: String? = null,

    @Json(name = "hosted_platform_version")
    val hostedPlatformVersion: String? = null,

    @Json(name = "hosted_sdk_version")
    val hostedSdkVersion: String? = null,

    @Json(name = "unity_build_id")
    val unityBuildId: String? = null,

    @Json(name = "launch_count")
    val launchCount: Int? = null,

    @Json(name = "environment_detail")
    val environmentDetail: String? = null,

    @Json(name = "network_log_body")
    val networkLogBody: String? = null,

    @Json(name = "network_encrypted_log_body")
    val networkEncryptedLogBody: String? = null,

    @Json(name = "device_manufacturer")
    val deviceManufacturer: String? = null,

    @Json(name = "device_model")
    val deviceModel: String? = null,

    @Json(name = "device_architecture")
    val deviceArchitecture: String? = null,

    @Json(name = "jailbroken")
    val jailbroken: Boolean? = null,

    @Json(name = "disk_total_capacity")
    val diskTotalCapacity: Long? = null,

    @Json(name = "os_type")
    val osType: String? = null,

    @Json(name = "os_version")
    val osVersion: String? = null,

    @Json(name = "os_alternate_type")
    val osAlternateType: String? = null,

    @Json(name = "os_code")
    val osCode: String? = null,

    @Json(name = "os_build")
    val osBuild: String? = null,

    @Json(name = "screen_resolution")
    val screenResolution: String? = null,

    @Json(name = "num_cores")
    val numCores: Int? = null,
)
