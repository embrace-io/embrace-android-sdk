package io.embrace.android.embracesdk.internal.logs

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Serializable representation of [EmbraceLogBody]
 */
@JsonClass(generateAdapter = true)
internal data class EmbraceLogBody(
    @Json(name = "message")
    val message: String?
)
