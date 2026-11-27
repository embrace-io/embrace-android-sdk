package io.embrace.android.embracesdk.spans

/**
 * Categorize the broad reason a Span completed unsuccessfully.
 */
public enum class ErrorCode {
    /**
     * An application failure caused the Span to terminate
     */
    Failure,

    /**
     * The operation tracked by the Span was terminated because the user abandoned and canceled it before it can complete successfully.
     */
    UserAbandon,

    /**
     * The reason for the unsuccessful termination is unknown
     */
    Unknown,
    ;

    public companion object {
        @Deprecated("Use Failure", ReplaceWith("ErrorCode.Failure"))
        @JvmField
        public val FAILURE: ErrorCode = Failure

        @Deprecated("Use UserAbandon", ReplaceWith("ErrorCode.UserAbandon"))
        @JvmField
        public val USER_ABANDON: ErrorCode = UserAbandon

        @Deprecated("Use Unknown", ReplaceWith("ErrorCode.Unknown"))
        @JvmField
        public val UNKNOWN: ErrorCode = Unknown
    }
}
