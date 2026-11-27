package io.embrace.android.embracesdk

/**
 * Enum representing the end state of the last run of the application.
 */
public enum class LastRunEndState {
    /**
     * The SDK has not been started yet.
     */
    INVALID,

    /**
     * The last run resulted in a crash.
     */
    CRASH,

    /**
     * The last run did not result in a crash.
     */
    CLEAN_EXIT,
}
