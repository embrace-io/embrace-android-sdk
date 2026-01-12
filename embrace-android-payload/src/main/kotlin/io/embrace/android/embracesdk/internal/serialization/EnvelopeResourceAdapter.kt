package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

class EnvelopeResourceAdapter {

    @Suppress("UNCHECKED_CAST")
    @FromJson
    fun fromJson(
        reader: JsonReader,
    ): EnvelopeResource? {
        val raw = reader.readJsonValue() as? Map<String, Any?> ?: return null

        val extras = raw.filterKeys { it !in knownKeys }
            .mapValues { it.value?.toString() }
            .filterValues { it != null }

        return EnvelopeResource(
            appVersion = raw[KEY_APP_VERSION] as? String,
            appFramework = (raw[KEY_APP_FRAMEWORK] as? Number)?.toInt()
                ?.let { AppFramework.fromInt(it) },
            buildId = raw[KEY_BUILD_ID] as? String,
            appEcosystemId = raw[KEY_APP_ECOSYSTEM_ID] as? String,
            buildType = raw[KEY_BUILD_TYPE] as? String,
            buildFlavor = raw[KEY_BUILD_FLAVOR] as? String,
            environment = raw[KEY_ENVIRONMENT] as? String,
            bundleVersion = raw[KEY_BUNDLE_VERSION] as? String,
            sdkVersion = raw[KEY_SDK_VERSION] as? String,
            sdkSimpleVersion = (raw[KEY_SDK_SIMPLE_VERSION] as? Number)?.toInt(),
            reactNativeBundleId = raw[KEY_REACT_NATIVE_BUNDLE_ID] as? String,
            reactNativeVersion = raw[KEY_REACT_NATIVE_VERSION] as? String,
            javascriptPatchNumber = raw[KEY_JAVASCRIPT_PATCH_NUMBER] as? String,
            hostedPlatformVersion = raw[KEY_HOSTED_PLATFORM_VERSION] as? String,
            hostedSdkVersion = raw[KEY_HOSTED_SDK_VERSION] as? String,
            unityBuildId = raw[KEY_UNITY_BUILD_ID] as? String,
            deviceManufacturer = raw[KEY_DEVICE_MANUFACTURER] as? String,
            deviceModel = raw[KEY_DEVICE_MODEL] as? String,
            deviceArchitecture = raw[KEY_DEVICE_ARCHITECTURE] as? String,
            jailbroken = raw[KEY_JAILBROKEN] as? Boolean,
            diskTotalCapacity = (raw[KEY_DISK_TOTAL_CAPACITY] as? Number)?.toLong(),
            osType = raw[KEY_OS_TYPE] as? String,
            osName = raw[KEY_OS_NAME] as? String,
            osVersion = raw[KEY_OS_VERSION] as? String,
            osCode = raw[KEY_OS_CODE] as? String,
            screenResolution = raw[KEY_SCREEN_RESOLUTION] as? String,
            numCores = (raw[KEY_NUM_CORES] as? Number)?.toInt(),
            extras = extras as? Map<String, String> ?: emptyMap(),
        )
    }

    @ToJson
    fun toJson(writer: JsonWriter, obj: EnvelopeResource?) {
        if (obj == null) {
            writer.nullValue()
            return
        }
        with(writer) {
            beginObject()
            name(KEY_APP_VERSION).value(obj.appVersion)
            name(KEY_APP_FRAMEWORK).value(obj.appFramework?.value)
            name(KEY_BUILD_ID).value(obj.buildId)
            name(KEY_APP_ECOSYSTEM_ID).value(obj.appEcosystemId)
            name(KEY_BUILD_TYPE).value(obj.buildType)
            name(KEY_BUILD_FLAVOR).value(obj.buildFlavor)
            name(KEY_ENVIRONMENT).value(obj.environment)
            name(KEY_BUNDLE_VERSION).value(obj.bundleVersion)
            name(KEY_SDK_VERSION).value(obj.sdkVersion)
            name(KEY_SDK_SIMPLE_VERSION).value(obj.sdkSimpleVersion)
            name(KEY_REACT_NATIVE_BUNDLE_ID).value(obj.reactNativeBundleId)
            name(KEY_REACT_NATIVE_VERSION).value(obj.reactNativeVersion)
            name(KEY_JAVASCRIPT_PATCH_NUMBER).value(obj.javascriptPatchNumber)
            name(KEY_HOSTED_PLATFORM_VERSION).value(obj.hostedPlatformVersion)
            name(KEY_HOSTED_SDK_VERSION).value(obj.hostedSdkVersion)
            name(KEY_UNITY_BUILD_ID).value(obj.unityBuildId)
            name(KEY_DEVICE_MANUFACTURER).value(obj.deviceManufacturer)
            name(KEY_DEVICE_MODEL).value(obj.deviceModel)
            name(KEY_DEVICE_ARCHITECTURE).value(obj.deviceArchitecture)
            name(KEY_JAILBROKEN).value(obj.jailbroken)
            name(KEY_DISK_TOTAL_CAPACITY).value(obj.diskTotalCapacity)
            name(KEY_OS_TYPE).value(obj.osType)
            name(KEY_OS_NAME).value(obj.osName)
            name(KEY_OS_VERSION).value(obj.osVersion)
            name(KEY_OS_CODE).value(obj.osCode)
            name(KEY_SCREEN_RESOLUTION).value(obj.screenResolution)
            name(KEY_NUM_CORES).value(obj.numCores)

            obj.extras.forEach { entry ->
                name(entry.key).value(entry.value)
            }
            endObject()
        }
    }

    private companion object {
        const val KEY_APP_VERSION = "app_version"
        const val KEY_APP_FRAMEWORK = "app_framework"
        const val KEY_BUILD_ID = "build_id"
        const val KEY_APP_ECOSYSTEM_ID = "app_ecosystem_id"
        const val KEY_BUILD_TYPE = "build_type"
        const val KEY_BUILD_FLAVOR = "build_flavor"
        const val KEY_ENVIRONMENT = "environment"
        const val KEY_BUNDLE_VERSION = "bundle_version"
        const val KEY_SDK_VERSION = "sdk_version"
        const val KEY_SDK_SIMPLE_VERSION = "sdk_simple_version"
        const val KEY_REACT_NATIVE_BUNDLE_ID = "react_native_bundle_id"
        const val KEY_REACT_NATIVE_VERSION = "react_native_version"
        const val KEY_JAVASCRIPT_PATCH_NUMBER = "javascript_patch_number"
        const val KEY_HOSTED_PLATFORM_VERSION = "hosted_platform_version"
        const val KEY_HOSTED_SDK_VERSION = "hosted_sdk_version"
        const val KEY_UNITY_BUILD_ID = "unity_build_id"
        const val KEY_DEVICE_MANUFACTURER = "device_manufacturer"
        const val KEY_DEVICE_MODEL = "device_model"
        const val KEY_DEVICE_ARCHITECTURE = "device_architecture"
        const val KEY_JAILBROKEN = "jailbroken"
        const val KEY_DISK_TOTAL_CAPACITY = "disk_total_capacity"
        const val KEY_OS_TYPE = "os_type"
        const val KEY_OS_NAME = "os_name"
        const val KEY_OS_VERSION = "os_version"
        const val KEY_OS_CODE = "os_code"
        const val KEY_SCREEN_RESOLUTION = "screen_resolution"
        const val KEY_NUM_CORES = "num_cores"

        val knownKeys = setOf(
            KEY_APP_VERSION,
            KEY_APP_FRAMEWORK,
            KEY_BUILD_ID,
            KEY_APP_ECOSYSTEM_ID,
            KEY_BUILD_TYPE,
            KEY_BUILD_FLAVOR,
            KEY_ENVIRONMENT,
            KEY_BUNDLE_VERSION,
            KEY_SDK_VERSION,
            KEY_SDK_SIMPLE_VERSION,
            KEY_REACT_NATIVE_BUNDLE_ID,
            KEY_REACT_NATIVE_VERSION,
            KEY_JAVASCRIPT_PATCH_NUMBER,
            KEY_HOSTED_PLATFORM_VERSION,
            KEY_HOSTED_SDK_VERSION,
            KEY_UNITY_BUILD_ID,
            KEY_DEVICE_MANUFACTURER,
            KEY_DEVICE_MODEL,
            KEY_DEVICE_ARCHITECTURE,
            KEY_JAILBROKEN,
            KEY_DISK_TOTAL_CAPACITY,
            KEY_OS_TYPE,
            KEY_OS_NAME,
            KEY_OS_VERSION,
            KEY_OS_CODE,
            KEY_SCREEN_RESOLUTION,
            KEY_NUM_CORES,
        )
    }
}
