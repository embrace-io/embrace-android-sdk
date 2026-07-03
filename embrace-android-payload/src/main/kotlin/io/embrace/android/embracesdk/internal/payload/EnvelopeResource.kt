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
 */

package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.serialization.EnvelopeResourceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Immutable attributes about the app, device, and Embrace SDK internal state for the duration of an app instance.
 *
 * Represented as a flat map of attribute key to value with the type preserved using [JsonPrimitive].
 */
@Serializable(with = EnvelopeResourceSerializer::class)
data class EnvelopeResource(
    val attributes: Map<String, JsonPrimitive> = emptyMap(),
)
