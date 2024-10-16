package io.embrace.android.embracesdk.internal.comms.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiRequestV2(
    val contentType: String = "application/json",
    val userAgent: String,
    val contentEncoding: String? = null,
    val accept: String = "application/json",
    val appId: String? = null,
    val deviceId: String? = null,
    val url: String,
) {
    fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Accept" to accept,
            "User-Agent" to userAgent,
            "Content-Type" to contentType
        )
        contentEncoding?.let { headers["Content-Encoding"] = it }
        appId?.let { headers["X-EM-AID"] = it }
        deviceId?.let { headers["X-EM-DID"] = it }
        return headers
    }
}
