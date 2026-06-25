package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NetworkCapturedCall(
    /**
     * The duration of the network request in milliseconds.
     */
    @SerialName("dur")
    val duration: Long? = null,

    /**
     * The end time of the request.
     */
    @SerialName("et")
    val endTime: Long? = null,

    /**
     * The HTTP method the network request corresponds to.
     */
    @SerialName("m")
    val httpMethod: String? = null,

    /**
     * The matched URL from the rule.
     */
    @SerialName("mu")
    val matchedUrl: String? = null,

    /**
     * UUID identifying the network request captured.
     */
    @SerialName("id")
    val networkId: String = UUID.randomUUID().toString(),

    /**
     * Request body.
     */
    @SerialName("qb")
    val requestBody: String? = null,

    /**
     * Captured request body size in bytes.
     */
    @SerialName("qi")
    val requestBodySize: Int? = null,

    /**
     * The query string for the request, if present.
     */
    @SerialName("qq")
    val requestQuery: String? = null,

    /**
     * A dictionary containing the HTTP query headers.
     */
    @SerialName("qh")
    val requestQueryHeaders: Map<String, String>? = null,

    /**
     * Request body size in bytes.
     */
    @SerialName("qz")
    val requestSize: Int? = null,

    /**
     * Contents of the body in a network request.
     */
    @SerialName("sb")
    val responseBody: String? = null,

    /**
     * Captured response body size in bytes.
     */
    @SerialName("si")
    val responseBodySize: Int? = null,

    /**
     * A dictionary containing the HTTP response headers.
     */
    @SerialName("sh")
    val responseHeaders: Map<String, String>? = null,

    /**
     * Response body size in bytes.
     */
    @SerialName("sz")
    val responseSize: Int? = null,

    /**
     * UUID identifying the network request captured.
     */
    @SerialName("sc")
    val responseStatus: Int? = null,

    /**
     * Session part ID that the network request occurred during.
     */
    @SerialName("sid")
    val sessionPartId: String? = null,

    /**
     * User Session ID that the network request occurred during.
     */
    @SerialName("usi")
    val userSessionId: String? = null,

    /**
     * The start time of the request.
     */
    @SerialName("st")
    val startTime: Long? = null,

    /**
     * The URL being requested.
     */
    @SerialName("url")
    val url: String? = null,

    /**
     * Error message in case the network call has failed.
     */
    @SerialName("em")
    val errorMessage: String? = null,

    /**
     * Encrypted data.
     */
    @SerialName("ne")
    val encryptedPayload: String? = null,
)
