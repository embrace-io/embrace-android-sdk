package io.embrace.android.embracesdk.logging

import android.util.Log

internal const val EMBRACE_TAG = "[Embrace]"

internal class AndroidLoggingAction : InternalEmbraceLogger.LoggerAction {
    override fun log(
        msg: String,
        severity: InternalEmbraceLogger.Severity,
        throwable: Throwable?,
        logStacktrace: Boolean
    ) {
        val exception = throwable?.takeIf { logStacktrace }
        when (severity) {
            InternalEmbraceLogger.Severity.DEBUG -> Log.d(EMBRACE_TAG, msg, exception)
            InternalEmbraceLogger.Severity.INFO -> Log.i(EMBRACE_TAG, msg, exception)
            InternalEmbraceLogger.Severity.WARNING -> Log.w(EMBRACE_TAG, msg, exception)
            InternalEmbraceLogger.Severity.ERROR -> Log.e(EMBRACE_TAG, msg, exception)
            else -> Log.v(EMBRACE_TAG, msg, exception)
        }
    }
}
