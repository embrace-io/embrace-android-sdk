package io.embrace.android.embracesdk.comms.api

import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.network.http.HttpMethod
import java.io.IOException

@JsonClass(generateAdapter = true)
internal data class ApiRequest(
    val contentType: String = "application/json",

    val userAgent: String = "Embrace/a/" + BuildConfig.VERSION_NAME,

    val contentEncoding: String? = null,

    val accept: String = "application/json",

    val acceptEncoding: String? = null,

    val appId: String? = null,

    val deviceId: String? = null,

    val eventId: String? = null,

    val logId: String? = null,

    val url: EmbraceUrl,

    val httpMethod: HttpMethod = HttpMethod.POST,

    val eTag: String? = null
) {

    fun getHeaders(): Map<String, String> {
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

    fun toConnection(): EmbraceConnection {
        try {
            val connection = url.openConnection()

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
    fun isSessionRequest(): Boolean = url.toString().endsWith("spans")
}
