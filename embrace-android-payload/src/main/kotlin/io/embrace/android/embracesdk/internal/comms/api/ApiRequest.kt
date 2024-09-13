package io.embrace.android.embracesdk.internal.comms.api

import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.network.http.HttpMethod

@JsonClass(generateAdapter = true)
data class ApiRequest(
    val contentType: String = "application/json",

    val userAgent: String,

    val contentEncoding: String? = null,

    val accept: String = "application/json",

    val acceptEncoding: String? = null,

    val appId: String? = null,

    val deviceId: String? = null,

    val url: ApiRequestUrl,

    val httpMethod: HttpMethod = HttpMethod.POST,

    val eTag: String? = null
)
