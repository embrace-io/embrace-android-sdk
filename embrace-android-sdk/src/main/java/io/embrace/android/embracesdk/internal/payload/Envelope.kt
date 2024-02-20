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
    val resource: EnvelopeResource,

    @Json(name = "metadata")
    val metadata: EnvelopeMetadata,

    @Json(name = "version")
    val version: String,

    @Json(name = "type")
    val type: String,

    @Json(name = "data")
    val data: T
)
