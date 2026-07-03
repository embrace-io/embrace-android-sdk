package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

class EnvelopeResourceSourceImpl(
    private val configService: ConfigService,
    private val hosted: HostedSdkVersionInfo,
    private val environment: AppEnvironment.Environment,
    private val device: Device,
    private val versionCode: Int?,
    private val rnBundleIdProvider: () -> String?,
    private val otelResourceAttributesProvider: () -> Map<String, String>,
) : EnvelopeResourceSource {

    private val extras = ConcurrentHashMap<String, JsonPrimitive>()

    override fun getEnvelopeResource(): EnvelopeResource {
        val buildInfo = configService.buildInfo

        // Custom envelope resource keys that are internal to Embrace and do not match attributes defined in OTel semantic conventions
        val embraceInternalAttributes = buildMap {
            putValue("app_framework", configService.appFramework.value)
            putValue("build_id", buildInfo.buildId)
            putValue("build_type", buildInfo.buildType)
            putValue("build_flavor", buildInfo.buildFlavor)
            putValue("bundle_version", buildInfo.versionCode)
            putValue("sdk_simple_version", versionCode)
            putValue("react_native_bundle_id", rnBundleIdProvider())
            putValue("javascript_patch_number", hosted.javaScriptPatchNumber)
            putValue("hosted_platform_version", hosted.hostedPlatformVersion)
            putValue("hosted_sdk_version", hosted.hostedSdkVersion)
            putValue("unity_build_id", hosted.unityBuildIdNumber)
            putValue("jailbroken", device.isJailbroken)
            putValue("disk_total_capacity", device.internalStorageTotalCapacity.value)
            putValue("screen_resolution", device.screenResolution)
            putValue("num_cores", device.numberOfCores)
            putValue("device_architecture", configService.cpuAbi.archName)
            putValue("environment", environment.value)

            // attributes set by hosted SDKs or other parts of the SDK that are not always present
            putAll(extras)
        }

        /**
         * Combines OTel resource attributes and the additional Embrace internal attributes that aren't in the OTel Resource.
         */
        val consolidatedAttributes = buildMap {
            otelResourceAttributesProvider().forEach { (key, value) ->
                put(key, JsonPrimitive(value))
            }

            embraceInternalAttributes.forEach { (legacyKey, value) ->
                put(PROPOSED_SEM_CONV_ATTRIBUTES[legacyKey] ?: legacyKey, value)
            }
        }

        return EnvelopeResource(consolidatedAttributes)
    }

    override fun add(key: String, value: String) {
        extras[key] = JsonPrimitive(value)
    }

    private fun MutableMap<String, JsonPrimitive>.putValue(key: String, value: String?) {
        if (value != null) {
            put(key, JsonPrimitive(value))
        }
    }

    private fun MutableMap<String, JsonPrimitive>.putValue(key: String, value: Int?) {
        if (value != null) {
            put(key, JsonPrimitive(value))
        }
    }

    private fun MutableMap<String, JsonPrimitive>.putValue(key: String, value: Long?) {
        if (value != null) {
            put(key, JsonPrimitive(value))
        }
    }

    private fun MutableMap<String, JsonPrimitive>.putValue(key: String, value: Boolean?) {
        if (value != null) {
            put(key, JsonPrimitive(value))
        }
    }

    private companion object {

        /**
         * Legacy internal Embrace attributes that should become OTel semantic conventions, but have not been accepted yet.
         */
        private val PROPOSED_SEM_CONV_ATTRIBUTES = mapOf(
            "environment" to "emb.app.environment",
        )
    }
}
