package io.embrace.android.embracesdk

/**
 * The severity of the log message.
 */
public enum class Severity {

    /**
     * Reports log messages with info level severity.
     */
    Info,

    /**
     * Reports log messages with warning level severity.
     */
    Warning,

    /**
     * Reports log messages with error level severity.
     */
    Error,
    ;

    public companion object {
        @Deprecated("Use Info", ReplaceWith("Severity.Info"))
        @JvmField
        public val INFO: Severity = Info

        @Deprecated("Use Warning", ReplaceWith("Severity.Warning"))
        @JvmField
        public val WARNING: Severity = Warning

        @Deprecated("Use Error", ReplaceWith("Severity.Error"))
        @JvmField
        public val ERROR: Severity = Error
    }
}
