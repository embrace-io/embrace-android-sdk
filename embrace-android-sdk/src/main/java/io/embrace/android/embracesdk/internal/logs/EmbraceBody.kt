package io.embrace.android.embracesdk.internal.logs

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Serializable representation of [EmbraceBody]
 */
@JsonClass(generateAdapter = true)
internal data class EmbraceBody(
    @Json(name = "message")
    val message: String?
)
