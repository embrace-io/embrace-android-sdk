package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.logs.LogPayload

/**
 * Envelope used for Embrace API requests for different types of data:
 * - [LogPayload]
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
)
