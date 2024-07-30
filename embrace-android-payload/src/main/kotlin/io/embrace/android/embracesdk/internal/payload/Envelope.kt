package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType

/**
 * Envelope used for Embrace API requests for different types of data:
 * - LogPayload
 * - SessionPayload
 * - BlobPayload
 * - CrashPayload
 * - NetworkCapturePayload
 */
@JsonClass(generateAdapter = true)
public data class Envelope<T>(
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

    public companion object {
        public val sessionEnvelopeType: ParameterizedType = Types.newParameterizedType(Envelope::class.java, SessionPayload::class.java)
    }
}
