package io.embrace.android.embracesdk.internal.delivery.execution

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiRequestV2(
    val contentType: String = "application/json",

    val userAgent: String,

    val contentEncoding: String? = null,

    val accept: String = "application/json",

    val acceptEncoding: String? = null,

    val appId: String? = null,

    val deviceId: String? = null,

    val eventId: String? = null,

    val logId: String? = null,

    val url: String,

    val httpMethod: HttpMethodV2 = HttpMethodV2.POST,

    val eTag: String? = null,
)

internal fun ApiRequestV2.getHeaders(): Map<String, String> {
    val headers = mutableMapOf(
        "Accept" to accept,
        "User-Agent" to userAgent,
        "Content-Type" to contentType
    )
    contentEncoding?.let { headers["Content-Encoding"] = it }
    acceptEncoding?.let { headers["Accept-Encoding"] = it }
    appId?.let { headers["X-EM-AID"] = it }
    deviceId?.let { headers["X-EM-DID"] = it }
    eventId?.let { headers["X-EM-SID"] = it }
    logId?.let { headers["X-EM-LID"] = it }
    eTag?.let { headers["If-None-Match"] = it }
    return headers
}
