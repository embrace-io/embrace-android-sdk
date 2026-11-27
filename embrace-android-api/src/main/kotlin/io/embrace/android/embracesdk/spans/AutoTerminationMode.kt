package io.embrace.android.embracesdk.spans

/**
 * Defines how the span should terminate automatically
 */
public enum class AutoTerminationMode {

    /**
     * The span will not terminate automatically
     */
    None,

    /**
     * The span will terminate when the app goes to the background
     */
    OnBackground,
    ;

    public companion object {
        @Deprecated("Use None", ReplaceWith("AutoTerminationMode.None"))
        @JvmField
        public val NONE: AutoTerminationMode = None

        @Deprecated("Use OnBackground", ReplaceWith("AutoTerminationMode.OnBackground"))
        @JvmField
        public val ON_BACKGROUND: AutoTerminationMode = OnBackground
    }
}
