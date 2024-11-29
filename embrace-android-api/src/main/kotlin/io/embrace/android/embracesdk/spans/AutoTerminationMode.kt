package io.embrace.android.embracesdk.spans

/**
 * Defines how the span should terminate automatically
 */
public enum class AutoTerminationMode {

    /**
     * The span will not terminate automatically
     */
    NONE,

    /**
     * The span will terminate when the app goes to the background
     */
    ON_BACKGROUND,
}
