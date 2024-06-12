package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.opentelemetry.embSessionId

/**
 * The session message, containing the session itself, as well as performance information about the
 * device which occurred during the session.
 */
@JsonClass(generateAdapter = true)
internal data class SessionMessage(
    @Json(name = "resource")
    val resource: EnvelopeResource? = null,

    @Json(name = "metadata")
    val metadata: EnvelopeMetadata? = null,

    @Json(name = "version")
    val newVersion: String? = null,

    @Json(name = "type")
    val type: String? = null,

    @Json(name = "data")
    val data: SessionPayload? = null
)

internal fun SessionMessage.getSessionSpan(): Span? {
    return data?.spans?.singleOrNull { it.hasFixedAttribute(EmbType.Ux.Session) }
}

internal fun SessionMessage.getSessionId(): String? {
    return getSessionSpan()?.attributes?.findAttributeValue(embSessionId.name)
}
