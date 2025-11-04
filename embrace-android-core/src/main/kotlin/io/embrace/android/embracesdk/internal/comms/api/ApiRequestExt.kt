package io.embrace.android.embracesdk.internal.comms.api

internal fun ApiRequest.getHeaders(): Map<String, String> {
    val headers = mutableMapOf(
        "Accept" to accept,
        "User-Agent" to userAgent,
        "Content-Type" to contentType
    )
    contentEncoding?.let { headers["Content-Encoding"] = it }
    acceptEncoding?.let { headers["Accept-Encoding"] = it }
    appId?.let { headers["X-EM-AID"] = it }
    deviceId?.let { headers["X-EM-DID"] = it }
    eTag?.let { headers["If-None-Match"] = it }
    return headers
}
