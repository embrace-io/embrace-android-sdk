package io.embrace.android.embracesdk.internal.comms.api

/**
 * ApiResponse is a sealed class that represents the result of an API call.
 */
public sealed class ApiResponse {

    /**
     * Returns true if the API call should be retried.
     */
    public val shouldRetry: Boolean
        get() = when (this) {
            is TooManyRequests -> true
            is Incomplete -> true
            is None -> true
            is Failure -> code in 500..599
            else -> false
        }

    /**
     * Represents an API call that returned a 200 OK status code.
     */
    public data class Success(val body: String?, val headers: Map<String, String>?) : ApiResponse()

    /**
     * Represents an API call that returned a 304 Not Modified status code.
     */
    public object NotModified : ApiResponse()

    /**
     * Represents an API call that returned a 413 Payload Too Large status code.
     */
    public object PayloadTooLarge : ApiResponse()

    /**
     * Represents an API call that returned a 429 Too Many Requests status code.
     */
    public data class TooManyRequests(val endpoint: Endpoint, val retryAfter: Long?) : ApiResponse()

    /**
     * Represents a failed API call. (status code 400-499 or 500-599 except 413 and 429)
     */
    public data class Failure(val code: Int, val headers: Map<String, String>?) : ApiResponse()

    /**
     * Represents an exception thrown while making the API call.
     */
    public data class Incomplete(val exception: Throwable) : ApiResponse()

    /**
     * No response was received
     */
    public object None : ApiResponse()
}
