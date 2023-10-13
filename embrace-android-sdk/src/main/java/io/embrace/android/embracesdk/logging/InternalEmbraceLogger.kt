package io.embrace.android.embracesdk.logging

import android.util.Log
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Severity
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wrapper for the Android [Log] utility.
 * Can only be used internally, it's not part of the public API.
 */

// Suppressing "Nothing to inline". These functions are used all around the codebase, pretty often, so we want them to
// perform as fast as possible.
@Suppress("NOTHING_TO_INLINE")
internal class InternalEmbraceLogger {
    private val defaultLogger = AndroidLogger()
    private val loggerActions = CopyOnWriteArrayList<LoggerAction>()

    init {
        setToDefault()
    }

    private var threshold = Severity.INFO

    interface LoggerAction {
        fun log(msg: String, severity: Severity, throwable: Throwable?, logStacktrace: Boolean)
    }

    inline fun addLoggerAction(action: LoggerAction) {
        loggerActions.add(action)
    }

    @JvmOverloads
    inline fun logDeveloper(className: String, msg: String, throwable: Throwable? = null) {
        log("[$className] $msg", Severity.DEVELOPER, throwable, true)
    }

    @JvmOverloads
    inline fun logDebug(msg: String, throwable: Throwable? = null) {
        log(msg, Severity.DEBUG, throwable, true)
    }

    inline fun logInfo(msg: String) {
        log(msg, Severity.INFO, null, true)
    }

    @JvmOverloads
    inline fun logWarning(msg: String, throwable: Throwable? = null, logStacktrace: Boolean = false) {
        log(msg, Severity.WARNING, throwable, logStacktrace)
    }

    @JvmOverloads
    inline fun logError(msg: String, throwable: Throwable? = null, logStacktrace: Boolean = false) {
        log(msg, Severity.ERROR, throwable, logStacktrace)
    }

    // Log with INFO severity that always contains a throwable as an internal exception to be sent to Grafana
    inline fun logInfoWithException(msg: String, throwable: Throwable? = null, logStacktrace: Boolean = false) {
        log(msg, Severity.INFO, throwable ?: InternalErrorLogger.NotAnException(msg), logStacktrace)
    }

    // Log with WARNING severity that always contains a throwable as an internal exception to be sent to Grafana
    inline fun logWarningWithException(msg: String, throwable: Throwable? = null, logStacktrace: Boolean = false) {
        log(msg, Severity.WARNING, throwable ?: InternalErrorLogger.NotAnException(msg), logStacktrace)
    }

    fun logSDKNotInitialized(action: String) {
        val msg = "Embrace SDK is not initialized yet, cannot $action."
        log(
            msg,
            Severity.ERROR,
            Throwable(msg),
            true
        )
    }

    /**
     * Logs a message.
     *
     * @param msg the message to log.
     * @param severity how severe the log is. If it's lower than the threshold, the message will not be logged.
     * @param throwable exception, if any.
     * @param logStacktrace should add the throwable to the logging
     */

    fun log(msg: String, severity: Severity, throwable: Throwable?, logStacktrace: Boolean) {
        if (shouldTriggerLoggerActions(severity)) {
            loggerActions.forEach {
                it.log(msg, severity, throwable, logStacktrace)
            }
        }
    }

    private fun shouldTriggerLoggerActions(severity: Severity) =
        ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED || severity >= threshold

    fun setThreshold(severity: Severity) {
        threshold = severity
    }

    internal fun setToDefault() {
        if (loggerActions.isNotEmpty()) {
            loggerActions.clear()
        }
        loggerActions.add(defaultLogger)
    }
}
