package io.embrace.android.embracesdk.logging

import android.util.Log

internal class ReportingLoggerAction(
    private val internalErrorService: InternalErrorService
) : InternalEmbraceLogger.LoggerAction {

    override fun log(
        msg: String,
        severity: InternalEmbraceLogger.Severity,
        throwable: Throwable?,
        logStacktrace: Boolean
    ) {
        if (throwable != null) {
            try {
                internalErrorService.handleInternalError(throwable)
            } catch (e: Throwable) {
                // Yo dawg, I heard you like to handle internal errors...
                Log.w(EMBRACE_TAG, msg, e)
            }
        }
    }

    class InternalError(msg: String) : Exception(msg)
    class NotAnException(msg: String) : Exception(msg)
}
