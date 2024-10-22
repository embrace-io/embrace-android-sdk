package io.embrace.android.embracesdk.spans

/**
 * Categorize the broad reason a Span completed unsuccessfully.
 */
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
    UNKNOWN
}
