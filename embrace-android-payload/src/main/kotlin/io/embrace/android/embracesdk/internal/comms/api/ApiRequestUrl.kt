package io.embrace.android.embracesdk.internal.comms.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class ApiRequestUrl(

    @Json(name = "url")
    public val url: String
)
