package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope used for Embrace API requests for different types of data:
 * - LogPayload
 * - SessionPartPayload
 * - BlobPayload
 * - CrashPayload
 * - NetworkCapturePayload
 */
@Serializable
@JsonClass(generateAdapter = true)
data class Envelope<T>(
    @SerialName("resource")
    @Json(name = "resource")
    val resource: EnvelopeResource? = null,

    @SerialName("metadata")
    @Json(name = "metadata")
    val metadata: EnvelopeMetadata? = null,

    @SerialName("version")
    @Json(name = "version")
    val version: String? = null,

    @SerialName("type")
    @Json(name = "type")
    val type: String? = null,

    @SerialName("data")
    @Json(name = "data")
    val data: T,
) {

    companion object {
        val sessionEnvelopeSerializer: KSerializer<Envelope<SessionPartPayload>> =
            serializer(SessionPartPayload.serializer())

        val logEnvelopeSerializer: KSerializer<Envelope<LogPayload>> =
            serializer(LogPayload.serializer())

        fun LogPayload.createLogEnvelope(resource: EnvelopeResource, metadata: EnvelopeMetadata) =
            Envelope(
                resource = resource,
                metadata = metadata,
                version = "0.1.0",
                type = "logs",
                data = this
            )
    }
}
