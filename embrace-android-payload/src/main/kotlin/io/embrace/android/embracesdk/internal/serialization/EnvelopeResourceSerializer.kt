package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.EnvelopeResourceValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Serializer for [EnvelopeResource] that writes each attribute as a top-level key of the `resource`
 * JSON object, and reads every top-level key back into [EnvelopeResource.attributes].
 *
 * The serializer is key-agnostic: it relies on the JSON itself to carry the value type, so it does not
 * need a fixed schema of known keys. This is the only place that bridges [EnvelopeResourceValue] to the
 * JSON representation [JsonPrimitive]. Unrecognizable types are encoded as strings. Null entries are dropped on read.
 */
internal object EnvelopeResourceSerializer : KSerializer<EnvelopeResource> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.embrace.android.embracesdk.internal.payload.EnvelopeResource")

    override fun serialize(encoder: Encoder, value: EnvelopeResource) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("EnvelopeResourceSerializer can only be used with JSON.")
        val obj = buildJsonObject {
            value.attributes.forEach { (key, attr) ->
                put(key, attr.toJsonPrimitive())
            }
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): EnvelopeResource {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("EnvelopeResourceSerializer can only be used with JSON.")
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val attributes = buildMap {
            obj.forEach { (key, element) ->
                if (element is JsonPrimitive && element !== JsonNull) {
                    put(key, element.toResourceValue())
                }
            }
        }
        return EnvelopeResource(attributes)
    }

    private fun EnvelopeResourceValue.toJsonPrimitive(): JsonPrimitive = when (val v = value) {
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    private fun JsonPrimitive.toResourceValue(): EnvelopeResourceValue = when {
        isString -> EnvelopeResourceValue.of(content)
        booleanOrNull != null -> EnvelopeResourceValue.of(booleanOrNull == true)
        longOrNull != null -> EnvelopeResourceValue.of(requireNotNull(longOrNull))
        else -> EnvelopeResourceValue.of(content)
    }
}
