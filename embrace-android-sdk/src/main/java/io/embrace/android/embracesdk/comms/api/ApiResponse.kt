package io.embrace.android.embracesdk.comms.api

/**
 * ApiResponse is a sealed class that represents the result of an API call.
 */
internal sealed class ApiResponse {
    /**
     * Represents a successful API call (status code 200-299)
     */
    data class Success(val body: String?, val headers: Map<String, String>?) : ApiResponse()

    /**
     * Represents a failed API call. (status code 400-499 or 500-599)
     */
    data class Failure(val errorMessage: String?, val code: Int, val headers: Map<String, String>?) : ApiResponse()

    /**
     * Represents an exception thrown while making the API call.
     */
    data class Incomplete(val exception: Throwable) : ApiResponse()
}
