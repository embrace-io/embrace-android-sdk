package io.embrace.android.embracesdk.logging

import android.util.Log

private const val EMBRACE_TAG = "[Embrace]"
private const val DEVELOPER_EMBRACE_TAG = "[EmbraceDev]"

internal class AndroidLogger : InternalEmbraceLogger.LoggerAction {
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
            InternalEmbraceLogger.Severity.DEVELOPER -> Log.d(DEVELOPER_EMBRACE_TAG, msg, exception)
            else -> Log.e(EMBRACE_TAG, msg, exception)
        }
    }
}
