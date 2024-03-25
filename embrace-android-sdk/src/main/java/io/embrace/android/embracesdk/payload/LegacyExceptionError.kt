package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.payload.InternalError

/**
 * Describes an Exception Error with a count of occurrences and a list of exceptions (causes).
 */
@JsonClass(generateAdapter = true)
internal data class LegacyExceptionError(@Transient private val logStrictMode: Boolean = false) {

    @Json(name = "c")
    var occurrences = 0

    @Json(name = "rep")
    var exceptionErrors = mutableListOf<LegacyExceptionErrorInfo>()

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
                LegacyExceptionErrorInfo(
                    clock.now(),
                    appState,
                    getExceptionInfo(ex)
                )
            )
        }
    }

    private fun getExceptionInfo(ex: Throwable?): List<LegacyExceptionInfo> {
        val result = mutableListOf<LegacyExceptionInfo>()
        var throwable: Throwable? = ex
        while (throwable != null && throwable != throwable.cause) {
            val exceptionInfo = LegacyExceptionInfo.ofThrowable(throwable)
            result.add(0, exceptionInfo)
            throwable = throwable.cause
        }
        return result
    }

    fun toNewPayload(): InternalError {
        return InternalError(occurrences, exceptionErrors.map(LegacyExceptionErrorInfo::toNewPayload))
    }
}

/**
 * The occurrences list limit.
 */
private const val DEFAULT_EXCEPTION_ERROR_LIMIT = 10
private const val DEFAULT_EXCEPTION_ERROR_LIMIT_STRICT_MODE = 50
