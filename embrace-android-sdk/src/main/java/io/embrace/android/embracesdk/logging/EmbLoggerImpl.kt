package io.embrace.android.embracesdk.logging

import android.util.Log
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.logging.EmbLogger.Severity

internal const val EMBRACE_TAG = "[Embrace]"

/**
 * Implementation of [EmbLogger] that logs to Android logcat & also allows tracking of internal
 * errors for our telemetry.
 */
internal class EmbLoggerImpl : EmbLogger {

    override var internalErrorService: InternalErrorService? = null

    override fun logDebug(msg: String, throwable: Throwable?) {
        log(msg, Severity.DEBUG, throwable, true)
    }

    override fun logInfo(msg: String) {
        log(msg, Severity.INFO, null, false)
    }

    override fun logWarning(msg: String, throwable: Throwable?, logStacktrace: Boolean) {
        log(msg, Severity.WARNING, throwable, logStacktrace)
    }

    override fun logError(msg: String, throwable: Throwable?, logStacktrace: Boolean) {
        log(msg, Severity.ERROR, throwable, logStacktrace)
    }

    override fun logSdkNotInitialized(action: String) {
        val msg = "Embrace SDK is not initialized yet, cannot $action."
        log(msg, Severity.WARNING, Throwable(msg), true)
    }

    override fun trackInternalError(msg: String, throwable: Throwable, severity: Severity) {
        try {
            internalErrorService?.handleInternalError(throwable)
        } catch (exc: Throwable) {
            // don't cause a crash loop!
            Log.w(EMBRACE_TAG, msg, exc)
        }
    }

    /**
     * Logs a message.
     *
     * @param msg the message to log.
     * @param severity how severe the log is. If it's lower than the threshold, the message will not be logged.
     * @param throwable exception, if any.
     * @param logStacktrace should add the throwable to the logging
     */
    @Suppress("NOTHING_TO_INLINE") // hot path - optimize by inlining
    private inline fun log(msg: String, severity: Severity, throwable: Throwable?, logStacktrace: Boolean) {
        if (severity >= Severity.INFO || ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED) {
            logcatImpl(throwable, logStacktrace, severity, msg)

            // report to internal error service if necessary
            if (throwable != null) {
                trackInternalError(msg, throwable, severity)
            }
        }
    }

    /**
     * Logs a message to the Android logcat.
     */
    @Suppress("NOTHING_TO_INLINE") // hot path - optimize by inlining
    private inline fun logcatImpl(
        throwable: Throwable?,
        logStacktrace: Boolean,
        severity: Severity,
        msg: String
    ) {
        val exception = throwable?.takeIf { logStacktrace }
        when (severity) {
            Severity.DEBUG -> Log.d(EMBRACE_TAG, msg, exception)
            Severity.INFO -> Log.i(EMBRACE_TAG, msg, exception)
            Severity.WARNING -> Log.w(EMBRACE_TAG, msg, exception)
            Severity.ERROR -> Log.e(EMBRACE_TAG, msg, exception)
        }
    }
}
