package io.embrace.android.embracesdk.logging

import android.util.Log

private const val EMBRACE_TAG = "[Embrace]"
private const val DEVELOPER_EMBRACE_TAG = "[EmbraceDev]"

internal class AndroidLogger : InternalEmbraceLogger.LoggerAction {
    override fun log(
        msg: String,
        severity: InternalStaticEmbraceLogger.Severity,
        throwable: Throwable?,
        logStacktrace: Boolean
    ) {
        val exception = throwable?.takeIf { logStacktrace }
        when (severity) {
            InternalStaticEmbraceLogger.Severity.DEBUG -> Log.d(EMBRACE_TAG, msg, exception)
            InternalStaticEmbraceLogger.Severity.INFO -> Log.i(EMBRACE_TAG, msg, exception)
            InternalStaticEmbraceLogger.Severity.WARNING -> Log.w(EMBRACE_TAG, msg, exception)
            InternalStaticEmbraceLogger.Severity.DEVELOPER -> Log.d(DEVELOPER_EMBRACE_TAG, msg, exception)
            else -> Log.e(EMBRACE_TAG, msg, exception)
        }
    }
}
