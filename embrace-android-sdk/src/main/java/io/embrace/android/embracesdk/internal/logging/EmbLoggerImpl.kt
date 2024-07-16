package io.embrace.android.embracesdk.internal.logging

import android.util.Log
import io.embrace.android.embracesdk.internal.ApkToolsConfig

internal const val EMBRACE_TAG = "[Embrace]"

/**
 * Implementation of [EmbLogger] that logs to Android logcat & also allows tracking of internal
 * errors for our telemetry.
 */
internal class EmbLoggerImpl : EmbLogger {

    override var internalErrorService: InternalErrorHandler? = null

    override fun logDebug(msg: String, throwable: Throwable?) {
        log(msg, EmbLogger.Severity.DEBUG, throwable)
    }

    override fun logInfo(msg: String, throwable: Throwable?) {
        log(msg, EmbLogger.Severity.INFO, throwable)
    }

    override fun logWarning(msg: String, throwable: Throwable?) {
        log(msg, EmbLogger.Severity.WARNING, throwable)
    }

    override fun logError(msg: String, throwable: Throwable?) {
        log(msg, EmbLogger.Severity.ERROR, throwable)
    }

    override fun logSdkNotInitialized(action: String) {
        val msg = "Embrace SDK is not initialized yet, cannot $action."
        log(msg, EmbLogger.Severity.WARNING, Throwable(msg))
    }

    override fun trackInternalError(type: InternalErrorType, throwable: Throwable) {
        try {
            internalErrorService?.handleInternalError(throwable)
        } catch (exc: Throwable) {
            // don't cause a crash loop!
            Log.w(EMBRACE_TAG, "Failed to track internal error", exc)
        }
    }

    /**
     * Logs a message.
     *
     * @param msg the message to log.
     * @param severity how severe the log is. If it's lower than the threshold, the message will not be logged.
     * @param throwable exception, if any.
     */
    @Suppress("NOTHING_TO_INLINE") // hot path - optimize by inlining
    private inline fun log(msg: String, severity: EmbLogger.Severity, throwable: Throwable?) {
        if (severity >= EmbLogger.Severity.INFO || ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED) {
            logcatImpl(throwable, severity, msg)
        }
    }

    /**
     * Logs a message to the Android logcat.
     */
    @Suppress("NOTHING_TO_INLINE") // hot path - optimize by inlining
    private inline fun logcatImpl(
        throwable: Throwable?,
        severity: EmbLogger.Severity,
        msg: String
    ) {
        when (severity) {
            EmbLogger.Severity.DEBUG -> Log.d(EMBRACE_TAG, msg, throwable)
            EmbLogger.Severity.INFO -> Log.i(EMBRACE_TAG, msg, throwable)
            EmbLogger.Severity.WARNING -> Log.w(EMBRACE_TAG, msg, throwable)
            EmbLogger.Severity.ERROR -> Log.e(EMBRACE_TAG, msg, throwable)
        }
    }
}
