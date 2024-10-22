package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.network.http.HttpMethod
import java.io.IOException

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

internal fun ApiRequest.toConnection(): EmbraceConnection {
    try {
        val connection = EmbraceUrl.create(url.url).openConnection()

        getHeaders().forEach {
            connection.setRequestProperty(it.key, it.value)
        }
        connection.setRequestMethod(httpMethod.name)
        if (httpMethod == HttpMethod.POST) {
            connection.setDoOutput(true)
        }
        return connection
    } catch (ex: IOException) {
        throw IllegalStateException(ex.localizedMessage ?: "", ex)
    }
}

/**
 * Returns true if the request is a session request. This heuristic should not be widely used
 * - it is only used to prioritise session requests over other requests.
 */
internal fun ApiRequest.isSessionRequest(): Boolean = url.url.endsWith("spans")
