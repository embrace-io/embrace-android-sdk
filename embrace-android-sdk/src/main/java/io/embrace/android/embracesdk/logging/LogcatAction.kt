package io.embrace.android.embracesdk.logging

import android.util.Log
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger.Severity

internal const val EMBRACE_TAG = "[Embrace]"

/**
 * Sends a log message from Embrace's log implementation to Logcat.
 */
internal class LogcatAction : InternalEmbraceLogger.LogAction {

    override fun log(
        msg: String,
        severity: Severity,
        throwable: Throwable?,
        logStacktrace: Boolean
    ) {
        val exception = throwable?.takeIf { logStacktrace }
        when (severity) {
            Severity.DEBUG -> Log.d(EMBRACE_TAG, msg, exception)
            Severity.INFO -> Log.i(EMBRACE_TAG, msg, exception)
            Severity.WARNING -> Log.w(EMBRACE_TAG, msg, exception)
            Severity.ERROR -> Log.e(EMBRACE_TAG, msg, exception)
            else -> Log.v(EMBRACE_TAG, msg, exception)
        }
    }
}
