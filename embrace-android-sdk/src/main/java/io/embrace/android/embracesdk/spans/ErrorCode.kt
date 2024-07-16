package io.embrace.android.embracesdk.spans

import io.embrace.android.embracesdk.annotation.BetaApi
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute

/**
 * Categorize the broad reason a Span completed unsuccessfully.
 */
@BetaApi
public enum class ErrorCode {
    /**
     * An application failure caused the Span to terminate
     */
    FAILURE,

    /**
     * The operation tracked by the Span was terminated because the user abandoned and canceled it before it can complete successfully.
     */
    USER_ABANDON,

    /**
     * The reason for the unsuccessful termination is unknown
     */
    UNKNOWN;

    internal fun fromErrorCode(): ErrorCodeAttribute = when (this) {
        FAILURE -> ErrorCodeAttribute.Failure
        USER_ABANDON -> ErrorCodeAttribute.UserAbandon
        UNKNOWN -> ErrorCodeAttribute.Unknown
    }
}
