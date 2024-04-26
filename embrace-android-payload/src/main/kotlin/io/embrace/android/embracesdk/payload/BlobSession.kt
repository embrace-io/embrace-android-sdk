package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class BlobSession(
    @Json(name = "si")
    val sessionId: String? = null
)
