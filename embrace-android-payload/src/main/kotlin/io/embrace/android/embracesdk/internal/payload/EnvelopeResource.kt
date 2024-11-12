/**
 * Embrace Envelope API
 *
 * The payloads we send to the Embrace backend do not map directly to an OTLP spec or even a specific concept.
 * Rather, they will contain objects that map to OTel concepts like resources, spans, and logs,
 * represented by a custom JSON serialization format that mirrors, more or less, the official Protobuf definitions.
 * But the structure within which these wrapper objects live will be Embrace-specific
 * and be tailored to our existing lifecycle.
 * In general, all payloads will share a common envelope that contains shared attributes that
 * we don’t want to duplicate -
 * largely what was included in appInfo, deviceInfo, and userInfo. It will also have a `data` object where custom,
 * payload-specific data can be stored, which is unique for each payload type
 *
 * The version of the OpenAPI document: 0.1.0
 * Contact: support@embrace.io
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Immutable attributes about the app, device, and Embrace SDK internal state for duration of an app launch.
 * Contains what previously was in AppInfo and DeviceInfo
 *
 * @param appVersion The app's publicly displayed version name. Previous name: a.v
 * @param appFramework The frameworks in use by the app. 1=Native, 2=React Native, 3=Unity. Previous name: a.f
 * @param buildId A unique ID for the build that is generated at build time. Previous name: a.bi
 * @param appEcosystemId Unique identifier for the app in its ecosystem.
 * In Apple, this is the Bundle ID (e.g. com.io.embrace).
 * In Android, this is the app's package name (e.g. io.embrace.testapp). Previous name: a.bid
 * @param buildType (Android) - the buildType name. Previous name: a.bt
 * @param buildFlavor (Android) - the flavor name. If productFlavors are not used this will be null. Previous name: a.fl
 * @param environment The name of the environment, i.e. dev or prod. Previous name: a.e
 * @param bundleVersion The app bundle version (on Android this is versionCode). Previous name: a.bv
 * @param sdkVersion The version number of the Embrace SDK. Previous name: a.sdk
 * @param sdkSimpleVersion The simple version number of the Embrace SDK. Previous name: a.sdc
 * @param reactNativeBundleId (React Native) the MD5 hash of the React Native bundle file. Previous name: a.rn
 * @param reactNativeVersion (React Native) the React Native version number. Previous name: a.rnv
 * @param javascriptPatchNumber (React Native) the JavaScript patch number. Previous name: a.jsp
 * @param hostedPlatformVersion The version of the hosted platform engine, i.e.
 * Unity/React Native/Flutter. Previous name: a.unv
 * @param hostedSdkVersion The version of the hosted SDK used. Previous name: a.usv
 * @param unityBuildId (Unity) the Unity build ID number. Previous name: a.ubg
 * @param launchCount (iOS) The number of times the SDK has been launched. Previous name: a.lc
 * @param environmentDetail (iOS) The name of the environment, i.e. dev or prod. Previous name: a.ed
 * @param deviceManufacturer The device manufacturer. Previous name: d.dm
 * @param deviceModel The device model. Previous name: d.do
 * @param deviceArchitecture The CPU architecture used by the device. Previous name: d.da
 * @param jailbroken Whether the device is rooted/jailbroken or not. Previous name: d.jb
 * @param diskTotalCapacity The total capacity of internal storage for the whole device. Previous name: d.ms
 * @param osType A hardcoded string representing the operating system in use. Previous name: d.os
 * @param osVersion The human readable OS version string. Previous name: d.ov
 * @param osAlternateType (iOS) The alternate OS type. Previous name: d.oa
 * @param osCode (Android) The OS version code. Previous name: d.oc
 * @param osBuild (iOS) The OS build code. Previous name: d.ob
 * @param screenResolution The screen resolution. Previous name: d.sr
 * @param numCores (Android) The number of CPU cores the device has. Previous name: d.nc
 */

@JsonClass(generateAdapter = true)
data class EnvelopeResource(

    /* The app's publicly displayed version name. Previous name: a.v */
    @Json(name = "app_version")
    val appVersion: String? = null,

    /* The frameworks in use by the app. 1=Native, 2=React Native, 3=Unity, 4=Flutter. Previous name: a.f */
    @Json(name = "app_framework")
    val appFramework: AppFramework? = null,

    /* A unique ID for the build that is generated at build time. Previous name: a.bi */
    @Json(name = "build_id")
    val buildId: String? = null,

    /* Unique identifier for the app in its ecosystem. In Apple, this is the Bundle ID (e.g. com.io.embrace).
    In Android, this is the app's package name (e.g. io.embrace.testapp). Previous name: a.bid */
    @Json(name = "app_ecosystem_id")
    val appEcosystemId: String? = null,

    /* (Android) - the buildType name. Previous name: a.bt */
    @Json(name = "build_type")
    val buildType: String? = null,

    /* (Android) - the flavor name. If productFlavors are not used this will be null. Previous name: a.fl */
    @Json(name = "build_flavor")
    val buildFlavor: String? = null,

    /* The name of the environment, i.e. dev or prod. Previous name: a.e */
    @Json(name = "environment")
    val environment: String? = null,

    /* The app bundle version (on Android this is versionCode). Previous name: a.bv */
    @Json(name = "bundle_version")
    val bundleVersion: String? = null,

    /* The version number of the Embrace SDK. Previous name: a.sdk */
    @Json(name = "sdk_version")
    val sdkVersion: String? = null,

    /* The simple version number of the Embrace SDK. Previous name: a.sdc */
    @Json(name = "sdk_simple_version")
    val sdkSimpleVersion: Int? = null,

    /* (React Native) the MD5 hash of the React Native bundle file. Previous name: a.rn */
    @Json(name = "react_native_bundle_id")
    val reactNativeBundleId: String? = null,

    /* (React Native) the React Native version number. Previous name: a.rnv */
    @Json(name = "react_native_version")
    val reactNativeVersion: String? = null,

    /* (React Native) the JavaScript patch number. Previous name: a.jsp */
    @Json(name = "javascript_patch_number")
    val javascriptPatchNumber: String? = null,

    /* The version of the hosted platform engine, i.e. Unity/React Native/Flutter. Previous name: a.unv */
    @Json(name = "hosted_platform_version")
    val hostedPlatformVersion: String? = null,

    /* The version of the hosted SDK used. Previous name: a.usv */
    @Json(name = "hosted_sdk_version")
    val hostedSdkVersion: String? = null,

    /* (Unity) the Unity build ID number. Previous name: a.ubg */
    @Json(name = "unity_build_id")
    val unityBuildId: String? = null,

    /* The device manufacturer. Previous name: d.dm */
    @Json(name = "device_manufacturer")
    val deviceManufacturer: String? = null,

    /* The device model. Previous name: d.do */
    @Json(name = "device_model")
    val deviceModel: String? = null,

    /* The CPU architecture used by the device. Previous name: d.da */
    @Json(name = "device_architecture")
    val deviceArchitecture: String? = null,

    /* Whether the device is rooted/jailbroken or not. Previous name: d.jb */
    @Json(name = "jailbroken")
    val jailbroken: Boolean? = null,

    /* The total capacity of internal storage for the whole device. Previous name: d.ms */
    @Json(name = "disk_total_capacity")
    val diskTotalCapacity: Long? = null,

    /* A hardcoded string representing type of operating system in use. */
    @Json(name = "os_type")
    val osType: String? = null,

    /* The user understood name of the OS - hardcoded to "android" for this SDK. Previous name: d.os */
    @Json(name = "os_name")
    val osName: String? = null,

    /* The human readable OS version string. Previous name: d.ov */
    @Json(name = "os_version")
    val osVersion: String? = null,

    /* (Android) The OS version code. Previous name: d.oc */
    @Json(name = "os_code")
    val osCode: String? = null,

    /* The screen resolution. Previous name: d.sr */
    @Json(name = "screen_resolution")
    val screenResolution: String? = null,

    /* (Android) The number of CPU cores the device has. Previous name: d.nc */
    @Json(name = "num_cores")
    val numCores: Int? = null
)
