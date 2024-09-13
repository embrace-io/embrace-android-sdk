package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.injection.SerializationAction

/**
 * A simple interface to make internal HTTP requests to the Embrace API
 */
interface ApiClient {
    /**
     * Executes [ApiRequest] as a GET, returning the response as a [ApiResponse]
     */
    fun executeGet(request: ApiRequest): ApiResponse

    /**
     * Executes [ApiRequest] as a POST with the supplied action that writes to an outputstream,
     * returning the response as a [ApiResponse]. The body will be gzip compressed.
     */
    fun executePost(request: ApiRequest, action: SerializationAction): ApiResponse

    companion object {

        const val NO_HTTP_RESPONSE: Int = -1

        const val TOO_MANY_REQUESTS: Int = 429

        const val defaultTimeoutMs: Int = 60 * 1000
    }
}
