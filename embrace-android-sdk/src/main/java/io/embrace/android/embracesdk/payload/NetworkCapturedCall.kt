package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import java.util.UUID

internal data class NetworkCapturedCall(
    /**
     * The duration of the network request in milliseconds.
     */
    @SerializedName("dur")
    val duration: Long? = null,

    /**
     * The end time of the request.
     */
    @SerializedName("et")
    val endTime: Long? = null,

    /**
     * The HTTP method the network request corresponds to.
     */
    @SerializedName("m")
    val httpMethod: String? = null,

    /**
     * The matched URL from the rule.
     */
    @SerializedName("mu")
    val matchedUrl: String? = null,

    /**
     * UUID identifying the network request captured.
     */
    @SerializedName("id")
    val networkId: String = UUID.randomUUID().toString(),

    /**
     * Request body.
     */
    @SerializedName("qb")
    val requestBody: String? = null,

    /**
     * Captured request body size in bytes.
     */
    @SerializedName("qi")
    val requestBodySize: Int? = null,

    /**
     * The query string for the request, if present.
     */
    @SerializedName("qq")
    val requestQuery: String? = null,

    /**
     * A dictionary containing the HTTP query headers.
     */
    @SerializedName("qh")
    val requestQueryHeaders: Map<String, String>? = null,

    /**
     * Request body size in bytes.
     */
    @SerializedName("qz")
    val requestSize: Int? = null,

    /**
     * Contents of the body in a network request.
     */
    @SerializedName("sb")
    val responseBody: String? = null,

    /**
     * Captured response body size in bytes.
     */
    @SerializedName("si")
    val responseBodySize: Int? = null,

    /**
     * A dictionary containing the HTTP response headers.
     */
    @SerializedName("sh")
    val responseHeaders: Map<String, String>? = null,

    /**
     * Response body size in bytes.
     */
    @SerializedName("sz")
    val responseSize: Int? = null,

    /**
     * UUID identifying the network request captured.
     */
    @SerializedName("sc")
    val responseStatus: Int? = null,

    /**
     * Session ID that the network request occurred during.
     */
    @SerializedName("sid")
    val sessionId: String? = null,

    /**
     * The start time of the request.
     */
    @SerializedName("st")
    val startTime: Long? = null,

    /**
     * The URL being requested.
     */
    @SerializedName("url")
    val url: String? = null,

    /**
     * Error message in case the network call has failed.
     */
    @SerializedName("em")
    val errorMessage: String? = null,

    /**
     * Encrypted data.
     */
    @SerializedName("ne")
    val encryptedPayload: String? = null
)
