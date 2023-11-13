package io.embrace.android.embracesdk.comms.api

/**
 * ApiResponse is a sealed class that represents the result of an API call.
 */
internal sealed class ApiResponse<out T> {
    /**
     * Represents a successful API call (status code 200-299)
     */
    data class Success<T>(val body: T?, val headers: Map<String, String>?) : ApiResponse<T>()

    /**
     * Represents a failed API call. (status code 400-499 or 500-599)
     */
    data class Failure(val errorMessage: String?, val code: Int, val headers: Map<String, String>?) : ApiResponse<Nothing>()

    /**
     * Represents an exception thrown while making the API call.
     */
    data class Error(val exception: Throwable) : ApiResponse<Nothing>()
}
