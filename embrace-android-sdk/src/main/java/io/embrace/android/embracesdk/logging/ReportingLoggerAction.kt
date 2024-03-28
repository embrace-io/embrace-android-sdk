package io.embrace.android.embracesdk.logging

import android.util.Log

internal class ReportingLoggerAction(
    private val internalErrorService: InternalErrorService,
    private val logStrictMode: Boolean = false
) : InternalEmbraceLogger.LoggerAction {

    override fun log(
        msg: String,
        severity: InternalEmbraceLogger.Severity,
        throwable: Throwable?,
        logStacktrace: Boolean
    ) {
        val finalThrowable = when {
            logStrictMode && severity == InternalEmbraceLogger.Severity.ERROR && throwable == null -> LogStrictModeException(
                msg
            )
            else -> throwable
        }

        if (finalThrowable != null) {
            try {
                internalErrorService.handleInternalError(finalThrowable)
            } catch (e: Throwable) {
                // Yo dawg, I heard you like to handle internal errors...
                Log.w(EMBRACE_TAG, msg, e)
            }
        }
    }

    class LogStrictModeException(msg: String) : Exception(msg)
    class InternalError(msg: String) : Exception(msg)
    class NotAnException(msg: String) : Exception(msg)
}
