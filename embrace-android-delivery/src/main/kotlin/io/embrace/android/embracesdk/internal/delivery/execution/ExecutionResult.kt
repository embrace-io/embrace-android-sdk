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
     * A completed HTTP request that returned a 413 (Payload Too Large) status code.
     */
    object PayloadTooLarge : ExecutionResult(false)

    /**
     * A completed HTTP request that returned a 429 (Too Many Requests) status code.
     */
    data class TooManyRequests(val endpoint: Endpoint, val retryAfter: Long?) : ExecutionResult(true)

    /**
     * A completed HTTP request that with a status code that indicates it did not succeed (4xx and 5xx)
     *
     * Other than 413 and 429, which are modeled by [PayloadTooLarge] and [TooManyRequests] respectively, all properly
     * received responses will be represented by this result.
     */
    data class Failure(val code: Int) : ExecutionResult(code in 500..599)

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
     * An execution attempt was not made for expected reasons. Any internal error logging will be handled at the
     * site where the error was found, and the caller can move on.
     */
    object NotAttempted : ExecutionResult(false)

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_ENTITY_TOO_LARGE = 413
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
                HTTP_ENTITY_TOO_LARGE -> PayloadTooLarge
                HTTP_TOO_MANY_REQUESTS -> TooManyRequests(
                    endpoint,
                    headersProvider()["Retry-After"]?.toLongOrNull()
                )

                in HTTP_FAILURES -> Failure(responseCode)
                else -> Other(responseCode)
            }
        }
    }
}
