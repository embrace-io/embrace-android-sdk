package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.Endpoint

/**
 * The result of an execution attempt on a [RequestExecutionService]
 */
sealed class ExecutionResult(
    val shouldRetry: Boolean,
) {
    /**
     * A completed HTTP request that returned a 2xx status code.
     */
    object Success : ExecutionResult(false)

    /**
     * A completed HTTP request that returned a 429 (Too Many Requests) status code.
     */
    data class TooManyRequests(val endpoint: Endpoint, val retryAfterMs: Long?) : ExecutionResult(true)

    /**
     * A completed HTTP request that with a status code that indicates it did not succeed (4xx and 5xx).
     *
     * Other than 429, which is modeled by [TooManyRequests], all properly received error responses will be represented by this result.
     *
     * Since Embrace servers are not configured to send back these codes, it is assumed that they are returned by something else
     * in the network hops in between the device and Embrace, and thus are potentially recoverable.
     */
    data class Failure(val code: Int) : ExecutionResult(true)

    /**
     * A completed HTTP request with a status code not explicitly covered by other [ExecutionResult]
     *
     * This indicates the request being completed but makes no assumption about whether the server would have handled
     * it correctly. By default, this should be treated similarly as [Success].
     */
    data class Other(val code: Int) : ExecutionResult(false)

    /**
     * An execution that was interrupted by the given throwable. This result may be due to a failure before the request
     * was queued for execution, during execution of the request, or in the job after the response was fully loaded.
     * Depending on the nature of the exception, we may or may not want to retry the send again. The code constructing
     * this result will determine that.
     */
    data class Incomplete(val exception: Throwable, val retry: Boolean) : ExecutionResult(retry)

    /**
     * Execution was not attempted because the network isn't ready. It should be sent at the next possible time.
     */
    object NetworkNotReady : ExecutionResult(true)

    /**
     * An execution attempt was not made for expected reasons. Any internal error logging will be handled at the
     * site where the error was found, and the caller can move on.
     */
    object NotAttempted : ExecutionResult(false)

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_FAILURES = 400..599

        /**
         * Given the inputs, return the [ExecutionResult] they map to
         */
        fun getResult(
            endpoint: Endpoint,
            responseCode: Int?,
            headersProvider: () -> Map<String, String>,
            executionError: Throwable? = null,
        ): ExecutionResult {
            return when (responseCode) {
                null -> Incomplete(
                    exception = executionError ?: IllegalStateException("Unknown execution error"),
                    retry = true
                )

                HTTP_OK -> Success
                HTTP_TOO_MANY_REQUESTS -> TooManyRequests(
                    endpoint,
                    headersProvider()["Retry-After"]?.toLongOrNull()?.times(1000)
                )

                in HTTP_FAILURES -> Failure(responseCode)
                else -> Other(responseCode)
            }
        }
    }
}
