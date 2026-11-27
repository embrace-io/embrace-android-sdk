package io.embrace.android.embracesdk

/**
 * Enum representing the end state of the last run of the application.
 */
public enum class LastRunEndState(public val value: Int) {
    /**
     * The SDK has not been started yet.
     */
    Invalid(0),

    /**
     * The last run resulted in a crash.
     */
    Crash(1),

    /**
     * The last run did not result in a crash.
     */
    CleanExit(2),
    ;

    public companion object {
        @Deprecated("Use Invalid", ReplaceWith("LastRunEndState.Invalid"))
        @JvmField
        public val INVALID: LastRunEndState = Invalid

        @Deprecated("Use Crash", ReplaceWith("LastRunEndState.Crash"))
        @JvmField
        public val CRASH: LastRunEndState = Crash

        @Deprecated("Use CleanExit", ReplaceWith("LastRunEndState.CleanExit"))
        @JvmField
        public val CLEAN_EXIT: LastRunEndState = CleanExit
    }
}
