package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Serializer for [EnvelopeResource] that writes each attribute as a top-level key of the `resource`
 * JSON object, and reads every top-level key back into [EnvelopeResource.attributes].
 *
 * The serializer is key-agnostic: it relies on the JSON itself to carry the value type, so it does not
 * need a fixed schema of known keys. Each attribute value preserves its JSON type (string / number /
 * boolean) via [JsonPrimitive]. JSON `null` entries are dropped on read.
 */
internal object EnvelopeResourceSerializer : KSerializer<EnvelopeResource> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.embrace.android.embracesdk.internal.payload.EnvelopeResource")

    override fun serialize(encoder: Encoder, value: EnvelopeResource) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("EnvelopeResourceSerializer can only be used with JSON.")
        val obj = buildJsonObject {
            value.attributes.forEach { (key, element) -> put(key, element) }
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
                    put(key, element)
                }
            }
        }
        return EnvelopeResource(attributes)
    }
}
