package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.internal.clock.Clock

/**
 * Describes an Exception Error with a count of occurrences and a list of exceptions (causes).
 */
internal data class ExceptionError(@Transient private val logStrictMode: Boolean) {
    @SerializedName("c")

    var occurrences = 0

    @SerializedName("rep")

    val exceptionErrors = mutableListOf<ExceptionErrorInfo>()

    /**
     * Add a new exception error info if exceptionError's size is below 20.
     * For each exceptions, occurrences is incremented by 1.
     *
     * @param ex       the exception error.
     * @param appState (foreground or background).
     */
    fun addException(ex: Throwable?, appState: String?, clock: Clock) {
        occurrences++
        var exceptionsLimits = DEFAULT_EXCEPTION_ERROR_LIMIT
        if (logStrictMode) {
            exceptionsLimits = DEFAULT_EXCEPTION_ERROR_LIMIT_STRICT_MODE
        }
        if (exceptionErrors.size < exceptionsLimits) {
            exceptionErrors.add(
                ExceptionErrorInfo(
                    clock.now(),
                    appState,
                    getExceptionInfo(ex)
                )
            )
        }
    }

    private fun getExceptionInfo(ex: Throwable?): List<ExceptionInfo> {
        val result = mutableListOf<ExceptionInfo>()
        var throwable: Throwable? = ex
        while (throwable != null && throwable != throwable.cause) {
            val exceptionInfo = ExceptionInfo.ofThrowable(throwable)
            result.add(0, exceptionInfo)
            throwable = throwable.cause
        }
        return result
    }
}

/**
 * The occurrences list limit.
 */
private const val DEFAULT_EXCEPTION_ERROR_LIMIT = 5
private const val DEFAULT_EXCEPTION_ERROR_LIMIT_STRICT_MODE = 50
