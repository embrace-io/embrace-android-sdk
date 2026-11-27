package io.embrace.android.embracesdk.spans

/**
 * Defines how the span should terminate automatically
 */
public sealed class AutoTerminationMode {

    /**
     * The span will not terminate automatically
     */
    public object None : AutoTerminationMode()

    /**
     * The span will terminate when the app goes to the background
     */
    public object OnBackground : AutoTerminationMode()

    /**
     * The span will be stopped with a failure error code once [timeoutMs] has elapsed since it started
     */
    public class Timeout(public val timeoutMs: Long) : AutoTerminationMode() {

        override fun equals(other: Any?): Boolean = other is Timeout && other.timeoutMs == timeoutMs

        override fun hashCode(): Int = timeoutMs.hashCode()

        override fun toString(): String = "Timeout(timeoutMs=$timeoutMs)"
    }

    public companion object {

        /**
         * The span will not terminate automatically
         */
        @Deprecated("Use AutoTerminationMode.None", ReplaceWith("AutoTerminationMode.None"))
        @JvmStatic
        public val NONE: AutoTerminationMode get() = None

        /**
         * The span will terminate when the app goes to the background
         */
        @Deprecated("Use AutoTerminationMode.OnBackground", ReplaceWith("AutoTerminationMode.OnBackground"))
        @JvmStatic
        public val ON_BACKGROUND: AutoTerminationMode get() = OnBackground
    }
}
