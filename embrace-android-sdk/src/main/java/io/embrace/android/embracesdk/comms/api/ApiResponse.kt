package io.embrace.android.embracesdk.comms.api

/**
 * ApiResponse is a sealed class that represents the result of an API call.
 */
internal sealed class ApiResponse {
    /**
     * Represents an API call that returned a 200 OK status code.
     */
    data class Success(val body: String?, val headers: Map<String, String>?) : ApiResponse()

    /**
     * Represents an API call that returned a 304 Not Modified status code.
     */
    object NotModified : ApiResponse()

    /**
     * Represents an API call that returned a 413 Payload Too Large status code.
     */
    object PayloadTooLarge : ApiResponse()

    /**
     * Represents an API call that returned a 429 Too Many Requests status code.
     */
    data class TooManyRequests(val retryAfter: Long?) : ApiResponse()

    /**
     * Represents a failed API call. (status code 400-499 or 500-599)
     */
    data class Failure(val code: Int, val headers: Map<String, String>?) : ApiResponse()

    /**
     * Represents an exception thrown while making the API call.
     */
    data class Incomplete(val exception: Throwable) : ApiResponse()
}
