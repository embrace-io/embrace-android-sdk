package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.opentelemetry.embSessionId

/**
 * Envelope used for Embrace API requests for different types of data:
 * - LogPayload
 * - SessionPayload
 * - BlobPayload
 * - CrashPayload
 * - NetworkCapturePayload
 */
@JsonClass(generateAdapter = true)
internal data class Envelope<T>(
    @Json(name = "resource")
    val resource: EnvelopeResource? = null,

    @Json(name = "metadata")
    val metadata: EnvelopeMetadata? = null,

    @Json(name = "version")
    val version: String? = null,

    @Json(name = "type")
    val type: String? = null,

    @Json(name = "data")
    val data: T
) {

    companion object {
        internal val sessionEnvelopeType = Types.newParameterizedType(Envelope::class.java, SessionPayload::class.java)
    }
}

internal fun Envelope<SessionPayload>.getSessionSpan(): Span? {
    return data.spans?.singleOrNull { it.hasFixedAttribute(EmbType.Ux.Session) }
}

internal fun Envelope<SessionPayload>.getSessionId(): String? {
    return getSessionSpan()?.attributes?.findAttributeValue(embSessionId.name)
}
