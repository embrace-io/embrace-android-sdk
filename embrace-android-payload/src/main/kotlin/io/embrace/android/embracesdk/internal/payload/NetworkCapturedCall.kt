package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@JsonClass(generateAdapter = true)
data class NetworkCapturedCall(
    /**
     * The duration of the network request in milliseconds.
     */
    @SerialName("dur")
    @Json(name = "dur")
    val duration: Long? = null,

    /**
     * The end time of the request.
     */
    @SerialName("et")
    @Json(name = "et")
    val endTime: Long? = null,

    /**
     * The HTTP method the network request corresponds to.
     */
    @SerialName("m")
    @Json(name = "m")
    val httpMethod: String? = null,

    /**
     * The matched URL from the rule.
     */
    @SerialName("mu")
    @Json(name = "mu")
    val matchedUrl: String? = null,

    /**
     * UUID identifying the network request captured.
     */
    @SerialName("id")
    @Json(name = "id")
    val networkId: String = UUID.randomUUID().toString(),

    /**
     * Request body.
     */
    @SerialName("qb")
    @Json(name = "qb")
    val requestBody: String? = null,

    /**
     * Captured request body size in bytes.
     */
    @SerialName("qi")
    @Json(name = "qi")
    val requestBodySize: Int? = null,

    /**
     * The query string for the request, if present.
     */
    @SerialName("qq")
    @Json(name = "qq")
    val requestQuery: String? = null,

    /**
     * A dictionary containing the HTTP query headers.
     */
    @SerialName("qh")
    @Json(name = "qh")
    val requestQueryHeaders: Map<String, String>? = null,

    /**
     * Request body size in bytes.
     */
    @SerialName("qz")
    @Json(name = "qz")
    val requestSize: Int? = null,

    /**
     * Contents of the body in a network request.
     */
    @SerialName("sb")
    @Json(name = "sb")
    val responseBody: String? = null,

    /**
     * Captured response body size in bytes.
     */
    @SerialName("si")
    @Json(name = "si")
    val responseBodySize: Int? = null,

    /**
     * A dictionary containing the HTTP response headers.
     */
    @SerialName("sh")
    @Json(name = "sh")
    val responseHeaders: Map<String, String>? = null,

    /**
     * Response body size in bytes.
     */
    @SerialName("sz")
    @Json(name = "sz")
    val responseSize: Int? = null,

    /**
     * UUID identifying the network request captured.
     */
    @SerialName("sc")
    @Json(name = "sc")
    val responseStatus: Int? = null,

    /**
     * Session ID that the network request occurred during.
     */
    @SerialName("sid")
    @Json(name = "sid")
    val sessionId: String? = null,

    /**
     * The start time of the request.
     */
    @SerialName("st")
    @Json(name = "st")
    val startTime: Long? = null,

    /**
     * The URL being requested.
     */
    @SerialName("url")
    @Json(name = "url")
    val url: String? = null,

    /**
     * Error message in case the network call has failed.
     */
    @SerialName("em")
    @Json(name = "em")
    val errorMessage: String? = null,

    /**
     * Encrypted data.
     */
    @SerialName("ne")
    @Json(name = "ne")
    val encryptedPayload: String? = null,
)
