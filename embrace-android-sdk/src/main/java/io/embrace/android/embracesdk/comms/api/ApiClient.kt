package io.embrace.android.embracesdk.comms.api

import java.io.ByteArrayInputStream

/**
 * A simple interface to make internal HTTP requests to the Embrace API
 */
internal interface ApiClient {
    /**
     * Executes [ApiRequest] as a GET, returning the response as a [ApiResponse]
     */
    fun executeGet(request: ApiRequest): ApiResponse

    /**
     * Executes [ApiRequest] as a POST with the given body defined by [payloadStream], returning the response as a [ApiResponse].
     * The body will be gzip compressed.
     */
    fun executePost(request: ApiRequest, payloadStream: ByteArrayInputStream): ApiResponse

    companion object {
        /**
         * The version of the API message format.
         */
        const val MESSAGE_VERSION = 13

        const val NO_HTTP_RESPONSE = -1

        const val TOO_MANY_REQUESTS = 429

        const val defaultTimeoutMs = 60 * 1000
    }
}
