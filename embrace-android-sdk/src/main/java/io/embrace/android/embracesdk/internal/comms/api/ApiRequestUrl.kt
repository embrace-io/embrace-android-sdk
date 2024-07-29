package io.embrace.android.embracesdk.internal.comms.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class ApiRequestUrl(

    @Json(name = "url")
    val url: String
)
