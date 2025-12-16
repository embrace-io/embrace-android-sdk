package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

@Suppress("UNCHECKED_CAST")
class EnvelopeResourceAdapter {

    @FromJson
    fun fromJson(
        reader: JsonReader,
        delegate: JsonAdapter<EnvelopeResource>,
    ): EnvelopeResource? {
        val raw = reader.readJsonValue() as? Map<String, Any?> ?: return null
        val value = delegate.fromJsonValue(raw) ?: return null

        /**** WARNING: when altering fields, please keep JSON keys in sync with [EnvelopeResource] ****/
        val knownKeys = setOf(
            "app_version",
            "app_build_number",
            "app_framework",
            "build_id",
            "app_ecosystem_id",
            "build_type",
            "build_flavor",
            "environment",
            "bundle_version",
            "sdk_version",
            "sdk_simple_version",
            "react_native_bundle_id",
            "react_native_version",
            "javascript_patch_number",
            "hosted_platform_version",
            "hosted_sdk_version",
            "unity_build_id",
            "device_manufacturer",
            "device_model",
            "device_architecture",
            "jailbroken",
            "disk_total_capacity",
            "os_type",
            "os_name",
            "os_version",
            "os_code",
            "screen_resolution",
            "num_cores",
        )
        val extras = (raw - knownKeys).mapValues { it.value.toString() }
        return value.copy(extras = extras)
    }

    @ToJson
    fun toJson(
        writer: JsonWriter,
        value: EnvelopeResource?,
        delegate: JsonAdapter<EnvelopeResource>,
    ) {
        if (value == null) {
            writer.nullValue()
            return
        }

        val json = delegate.toJsonValue(value) as MutableMap<String, Any?>
        json.putAll(value.extras)

        writer.beginObject()
        for ((k, v) in json) {
            writer.name(k)

            when (v) {
                is Long -> writer.value(v)
                is Int -> writer.value(v)
                is Boolean -> writer.value(v)
                else -> writer.value(v.toString())
            }
        }
        writer.endObject()
    }
}
