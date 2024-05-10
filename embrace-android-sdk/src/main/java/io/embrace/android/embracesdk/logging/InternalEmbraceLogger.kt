package io.embrace.android.embracesdk.logging

import android.util.Log
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import java.util.concurrent.CopyOnWriteArrayList

internal const val EMBRACE_TAG = "[Embrace]"

/**
 * Wrapper for the Android [Log] utility.
 * Can only be used internally, it's not part of the public API.
 */

// Suppressing "Nothing to inline". These functions are used all around the codebase, pretty often, so we want them to
// perform as fast as possible.
internal class InternalEmbraceLogger {
    private val logActions = CopyOnWriteArrayList<LogAction>(listOf())

    internal fun interface LogAction {
        fun log(msg: String, severity: Severity, throwable: Throwable?, logStacktrace: Boolean)
    }

    fun addLoggerAction(action: LogAction) {
        logActions.add(action)
    }

    @JvmOverloads
    fun logDebug(msg: String, throwable: Throwable? = null) {
        log(msg, Severity.DEBUG, throwable, true)
    }

    fun logInfo(msg: String) {
        log(msg, Severity.INFO, null, true)
    }

    @JvmOverloads
    fun logWarning(msg: String, throwable: Throwable? = null, logStacktrace: Boolean = false) {
        log(msg, Severity.WARNING, throwable, logStacktrace)
    }

    @JvmOverloads
    fun logError(msg: String, throwable: Throwable? = null, logStacktrace: Boolean = false) {
        log(msg, Severity.ERROR, throwable, logStacktrace)
    }

    fun logSdkNotInitialized(action: String) {
        val msg = "Embrace SDK is not initialized yet, cannot $action."
        log(msg, Severity.WARNING, Throwable(msg), true)
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

            logActions.forEach {
                it.log(msg, severity, throwable, logStacktrace)
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

    enum class Severity {
        DEBUG, INFO, WARNING, ERROR
    }
}
