package io.embrace.android.embracesdk.logging

import android.util.Log

/**
 * Sends a log message from Embrace's log implementation to [InternalErrorService],
 * if it contains a throwable.
 */
internal class InternalErrorServiceAction(
    private val internalErrorService: InternalErrorService
) : InternalEmbraceLogger.LogAction {

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
}
