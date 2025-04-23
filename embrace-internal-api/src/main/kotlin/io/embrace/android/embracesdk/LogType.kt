package io.embrace.android.embracesdk

import androidx.annotation.Keep

/**
 * Deprecated: use Severity instead. This enum is deprecated and will be removed in
 * a future release.
 *
 * Will flag the message as one of info, warning, or error for filtering on the dashboard
 */
@Keep
@Deprecated("")
enum class LogType {
    INFO,
    WARNING,
    ERROR
}
