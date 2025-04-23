package io.embrace.android.embracesdk

import androidx.annotation.Keep

/**
 * Enum representing the type of exception that occurred.
 * NONE is for a native android log, whether have or not an exception.
 * HANDLED or UNHANDLED are ONLY for Unity and Flutter handled and unhandled exceptions.
 *
 * @suppress
 */
@Keep
enum class LogExceptionType(val value: String) {
    NONE("none"),
    HANDLED("handled"),
    UNHANDLED("unhandled")
}
