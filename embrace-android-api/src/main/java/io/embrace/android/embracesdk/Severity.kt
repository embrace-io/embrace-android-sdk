package io.embrace.android.embracesdk

import androidx.annotation.Keep

/**
 * The severity of the log message.
 */
@Keep
public enum class Severity {

    /**
     * Reports log messages with info level severity.
     */
    INFO,

    /**
     * Reports log messages with warning level severity.
     */
    WARNING,

    /**
     * Reports log messages with error level severity.
     */
    ERROR
}
