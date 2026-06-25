package io.embrace.android.embracesdk.internal.payload

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
data class Envelope<T>(
    @SerialName("resource")
    val resource: EnvelopeResource? = null,

    @SerialName("metadata")
    val metadata: EnvelopeMetadata? = null,

    @SerialName("version")
    val version: String? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("data")
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
                data = this,
            )
    }
}
