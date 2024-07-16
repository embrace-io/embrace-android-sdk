package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class NetworkRequests(
    @Json(name = "v2") val networkSessionV2: NetworkSessionV2?
)
