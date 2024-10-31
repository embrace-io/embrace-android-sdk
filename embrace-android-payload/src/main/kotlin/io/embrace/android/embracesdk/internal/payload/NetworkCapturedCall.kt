package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class NetworkCapturedCall(
    /**
     * The duration of the network request in milliseconds.
     */
    @Json(name = "dur")
    val duration: Long? = null,

    /**
     * The end time of the request.
     */
    @Json(name = "et")
    val endTime: Long? = null,

    /**
     * The HTTP method the network request corresponds to.
     */
    @Json(name = "m")
    val httpMethod: String? = null,

    /**
     * The matched URL from the rule.
     */
    @Json(name = "mu")
    val matchedUrl: String? = null,

    /**
     * UUID identifying the network request captured.
     */
    @Json(name = "id")
    val networkId: String = UUID.randomUUID().toString(),

    /**
     * Request body.
     */
    @Json(name = "qb")
    val requestBody: String? = null,

    /**
     * Captured request body size in bytes.
     */
    @Json(name = "qi")
    val requestBodySize: Int? = null,

    /**
     * The query string for the request, if present.
     */
    @Json(name = "qq")
    val requestQuery: String? = null,

    /**
     * A dictionary containing the HTTP query headers.
     */
    @Json(name = "qh")
    val requestQueryHeaders: Map<String, String>? = null,

    /**
     * Request body size in bytes.
     */
    @Json(name = "qz")
    val requestSize: Int? = null,

    /**
     * Contents of the body in a network request.
     */
    @Json(name = "sb")
    val responseBody: String? = null,

    /**
     * Captured response body size in bytes.
     */
    @Json(name = "si")
    val responseBodySize: Int? = null,

    /**
     * A dictionary containing the HTTP response headers.
     */
    @Json(name = "sh")
    val responseHeaders: Map<String, String>? = null,

    /**
     * Response body size in bytes.
     */
    @Json(name = "sz")
    val responseSize: Int? = null,

    /**
     * UUID identifying the network request captured.
     */
    @Json(name = "sc")
    val responseStatus: Int? = null,

    /**
     * Session ID that the network request occurred during.
     */
    @Json(name = "sid")
    val sessionId: String? = null,

    /**
     * The start time of the request.
     */
    @Json(name = "st")
    val startTime: Long? = null,

    /**
     * The URL being requested.
     */
    @Json(name = "url")
    val url: String? = null,

    /**
     * Error message in case the network call has failed.
     */
    @Json(name = "em")
    val errorMessage: String? = null,

    /**
     * Encrypted data.
     */
    @Json(name = "ne")
    val encryptedPayload: String? = null,
)
