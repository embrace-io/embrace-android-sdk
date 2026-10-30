package io.embrace.android.embracesdk.internal.logging

import android.util.Log

internal const val EMBRACE_TAG = "[Embrace]"

/**
 * Logs [msg] and [throwable] to Android logcat at the level matching [severity].
 */
fun androidLogcat(severity: ErrorHandler.Severity, msg: String, throwable: Throwable?) {
    when (severity) {
        ErrorHandler.Severity.INFO -> Log.i(EMBRACE_TAG, msg, throwable)
        ErrorHandler.Severity.WARNING -> Log.w(EMBRACE_TAG, msg, throwable)
        ErrorHandler.Severity.ERROR -> Log.e(EMBRACE_TAG, msg, throwable)
    }
}
