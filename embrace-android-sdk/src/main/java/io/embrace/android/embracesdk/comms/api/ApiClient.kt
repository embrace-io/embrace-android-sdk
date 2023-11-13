package io.embrace.android.embracesdk.comms.api

/**
 * A simple interface to make internal HTTP requests to the Embrace API
 */
internal interface ApiClient {
    /**
     * Executes [ApiRequest] as a GET, returning the response as a [ApiResponse]
     */
    fun executeGet(request: ApiRequest): ApiResponse<String>

    /**
     * Executes [ApiRequest] as a POST with the given body defined by [payloadToCompress], returning the response as a [ApiResponse].
     * The body will be gzip compressed.
     */
    fun executePost(request: ApiRequest, payloadToCompress: ByteArray): ApiResponse<String>

    companion object {
        /**
         * The version of the API message format.
         */
        const val MESSAGE_VERSION = 13

        const val NO_HTTP_RESPONSE = -1

        const val defaultTimeoutMs = 60 * 1000
    }
}
