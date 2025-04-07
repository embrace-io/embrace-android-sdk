package io.embrace.android.embracesdk.flow

import io.embrace.android.embracesdk.spans.ErrorCode

/**
 * The result for a given [Flow]
 */
public enum class Result {
    /**
     * [Flow] ended normally
     */
    SUCCESS,

    /**
     * [Flow] ended due to an application error
     */
    ERROR,

    /**
     * [Flow] ended due to user abandonment
     */
    USER_ABANDON;

    public fun getSpanErrorCode(): ErrorCode? = when (this) {
        SUCCESS -> null
        ERROR -> ErrorCode.FAILURE
        USER_ABANDON -> ErrorCode.USER_ABANDON
    }
}
