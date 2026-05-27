package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Custom serializer for [EnvelopeResource] that mirrors the previous Moshi `EnvelopeResourceAdapter`.
 *
 * On write, the 27 known fields are emitted in declaration order (nulls omitted), followed by the
 * flattened [EnvelopeResource.extras] map as additional top-level keys (insertion order preserved).
 * [EnvelopeResource.appFramework] is encoded as its integer [AppFramework.value].
 *
 * On read, the 27 known keys are extracted and any remaining (non-null) key is folded back into
 * [EnvelopeResource.extras] as a stringified value. An unknown `app_framework` integer decodes to
 * null via [AppFramework.fromInt].
 */
internal object EnvelopeResourceSerializer : KSerializer<EnvelopeResource> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.embrace.android.embracesdk.internal.payload.EnvelopeResource")

    override fun serialize(encoder: Encoder, value: EnvelopeResource) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("EnvelopeResourceSerializer can only be used with JSON.")
        val obj = buildJsonObject {
            putIfPresent(KEY_APP_VERSION, value.appVersion)
            putIfPresent(KEY_APP_FRAMEWORK, value.appFramework?.value)
            putIfPresent(KEY_BUILD_ID, value.buildId)
            putIfPresent(KEY_APP_ECOSYSTEM_ID, value.appEcosystemId)
            putIfPresent(KEY_BUILD_TYPE, value.buildType)
            putIfPresent(KEY_BUILD_FLAVOR, value.buildFlavor)
            putIfPresent(KEY_ENVIRONMENT, value.environment)
            putIfPresent(KEY_BUNDLE_VERSION, value.bundleVersion)
            putIfPresent(KEY_SDK_VERSION, value.sdkVersion)
            putIfPresent(KEY_SDK_SIMPLE_VERSION, value.sdkSimpleVersion)
            putIfPresent(KEY_REACT_NATIVE_BUNDLE_ID, value.reactNativeBundleId)
            putIfPresent(KEY_REACT_NATIVE_VERSION, value.reactNativeVersion)
            putIfPresent(KEY_JAVASCRIPT_PATCH_NUMBER, value.javascriptPatchNumber)
            putIfPresent(KEY_HOSTED_PLATFORM_VERSION, value.hostedPlatformVersion)
            putIfPresent(KEY_HOSTED_SDK_VERSION, value.hostedSdkVersion)
            putIfPresent(KEY_UNITY_BUILD_ID, value.unityBuildId)
            putIfPresent(KEY_DEVICE_MANUFACTURER, value.deviceManufacturer)
            putIfPresent(KEY_DEVICE_MODEL, value.deviceModel)
            putIfPresent(KEY_DEVICE_ARCHITECTURE, value.deviceArchitecture)
            putIfPresent(KEY_JAILBROKEN, value.jailbroken)
            putIfPresent(KEY_DISK_TOTAL_CAPACITY, value.diskTotalCapacity)
            putIfPresent(KEY_OS_TYPE, value.osType)
            putIfPresent(KEY_OS_NAME, value.osName)
            putIfPresent(KEY_OS_VERSION, value.osVersion)
            putIfPresent(KEY_OS_CODE, value.osCode)
            putIfPresent(KEY_SCREEN_RESOLUTION, value.screenResolution)
            putIfPresent(KEY_NUM_CORES, value.numCores)
            value.extras.forEach { (key, entry) -> put(key, entry) }
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): EnvelopeResource {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("EnvelopeResourceSerializer can only be used with JSON.")
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val extras = buildMap {
            obj.forEach { (key, element) ->
                if (key !in KNOWN_KEYS && element !is JsonNull) {
                    put(key, (element as? JsonPrimitive)?.content ?: element.toString())
                }
            }
        }

        return EnvelopeResource(
            appVersion = obj.string(KEY_APP_VERSION),
            appFramework = obj.int(KEY_APP_FRAMEWORK)?.let(AppFramework::fromInt),
            buildId = obj.string(KEY_BUILD_ID),
            appEcosystemId = obj.string(KEY_APP_ECOSYSTEM_ID),
            buildType = obj.string(KEY_BUILD_TYPE),
            buildFlavor = obj.string(KEY_BUILD_FLAVOR),
            environment = obj.string(KEY_ENVIRONMENT),
            bundleVersion = obj.string(KEY_BUNDLE_VERSION),
            sdkVersion = obj.string(KEY_SDK_VERSION),
            sdkSimpleVersion = obj.int(KEY_SDK_SIMPLE_VERSION),
            reactNativeBundleId = obj.string(KEY_REACT_NATIVE_BUNDLE_ID),
            reactNativeVersion = obj.string(KEY_REACT_NATIVE_VERSION),
            javascriptPatchNumber = obj.string(KEY_JAVASCRIPT_PATCH_NUMBER),
            hostedPlatformVersion = obj.string(KEY_HOSTED_PLATFORM_VERSION),
            hostedSdkVersion = obj.string(KEY_HOSTED_SDK_VERSION),
            unityBuildId = obj.string(KEY_UNITY_BUILD_ID),
            deviceManufacturer = obj.string(KEY_DEVICE_MANUFACTURER),
            deviceModel = obj.string(KEY_DEVICE_MODEL),
            deviceArchitecture = obj.string(KEY_DEVICE_ARCHITECTURE),
            jailbroken = obj.boolean(KEY_JAILBROKEN),
            diskTotalCapacity = obj.long(KEY_DISK_TOTAL_CAPACITY),
            osType = obj.string(KEY_OS_TYPE),
            osName = obj.string(KEY_OS_NAME),
            osVersion = obj.string(KEY_OS_VERSION),
            osCode = obj.string(KEY_OS_CODE),
            screenResolution = obj.string(KEY_SCREEN_RESOLUTION),
            numCores = obj.int(KEY_NUM_CORES),
            extras = extras,
        )
    }

    private fun JsonObjectBuilder.putIfPresent(key: String, value: String?) {
        if (value != null) put(key, value)
    }

    private fun JsonObjectBuilder.putIfPresent(key: String, value: Number?) {
        if (value != null) put(key, value)
    }

    private fun JsonObjectBuilder.putIfPresent(key: String, value: Boolean?) {
        if (value != null) put(key, value)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private const val KEY_APP_VERSION = "app_version"
    private const val KEY_APP_FRAMEWORK = "app_framework"
    private const val KEY_BUILD_ID = "build_id"
    private const val KEY_APP_ECOSYSTEM_ID = "app_ecosystem_id"
    private const val KEY_BUILD_TYPE = "build_type"
    private const val KEY_BUILD_FLAVOR = "build_flavor"
    private const val KEY_ENVIRONMENT = "environment"
    private const val KEY_BUNDLE_VERSION = "bundle_version"
    private const val KEY_SDK_VERSION = "sdk_version"
    private const val KEY_SDK_SIMPLE_VERSION = "sdk_simple_version"
    private const val KEY_REACT_NATIVE_BUNDLE_ID = "react_native_bundle_id"
    private const val KEY_REACT_NATIVE_VERSION = "react_native_version"
    private const val KEY_JAVASCRIPT_PATCH_NUMBER = "javascript_patch_number"
    private const val KEY_HOSTED_PLATFORM_VERSION = "hosted_platform_version"
    private const val KEY_HOSTED_SDK_VERSION = "hosted_sdk_version"
    private const val KEY_UNITY_BUILD_ID = "unity_build_id"
    private const val KEY_DEVICE_MANUFACTURER = "device_manufacturer"
    private const val KEY_DEVICE_MODEL = "device_model"
    private const val KEY_DEVICE_ARCHITECTURE = "device_architecture"
    private const val KEY_JAILBROKEN = "jailbroken"
    private const val KEY_DISK_TOTAL_CAPACITY = "disk_total_capacity"
    private const val KEY_OS_TYPE = "os_type"
    private const val KEY_OS_NAME = "os_name"
    private const val KEY_OS_VERSION = "os_version"
    private const val KEY_OS_CODE = "os_code"
    private const val KEY_SCREEN_RESOLUTION = "screen_resolution"
    private const val KEY_NUM_CORES = "num_cores"

    private val KNOWN_KEYS = setOf(
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
