package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.injection.SerializationAction

/**
 * A simple interface to make internal HTTP requests to the Embrace API
 */
public interface ApiClient {
    /**
     * Executes [ApiRequest] as a GET, returning the response as a [ApiResponse]
     */
    public fun executeGet(request: ApiRequest): ApiResponse

    /**
     * Executes [ApiRequest] as a POST with the supplied action that writes to an outputstream,
     * returning the response as a [ApiResponse]. The body will be gzip compressed.
     */
    public fun executePost(request: ApiRequest, action: SerializationAction): ApiResponse

    public companion object {

        public const val NO_HTTP_RESPONSE: Int = -1

        public const val TOO_MANY_REQUESTS: Int = 429

        public const val defaultTimeoutMs: Int = 60 * 1000
    }
}
