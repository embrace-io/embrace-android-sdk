package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.payload.InternalError

/**
 * Describes an Exception Error with a count of occurrences and a list of exceptions (causes).
 */
@JsonClass(generateAdapter = true)
internal data class LegacyExceptionError(

    @Json(name = "c")
    var occurrences: Int = 0,

    @Json(name = "rep")
    var exceptionErrors: MutableList<LegacyExceptionErrorInfo> = mutableListOf()
) {

    /**
     * Add a new exception error info if exceptionError's size is below 20.
     * For each exceptions, occurrences is incremented by 1.
     *
     * @param ex       the exception error.
     */
    fun addException(ex: Throwable?, clock: Clock) {
        occurrences++
        if (exceptionErrors.size < DEFAULT_EXCEPTION_ERROR_LIMIT) {
            exceptionErrors.add(
                LegacyExceptionErrorInfo(
                    clock.now(),
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
