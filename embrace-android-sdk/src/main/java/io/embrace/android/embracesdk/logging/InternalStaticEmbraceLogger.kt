package io.embrace.android.embracesdk.logging

import android.util.Log

/**
 * Wrapper for the Android [Log] utility.
 * Can only be used internally, it's not part of the public API.
 */

// Suppressing "Nothing to inline". These functions are used all around the codebase, pretty often, so we want them to
// perform as fast as possible.
@Suppress("NOTHING_TO_INLINE")
internal class InternalStaticEmbraceLogger private constructor() {

    enum class Severity {
        DEVELOPER, DEBUG, INFO, WARNING, ERROR, NONE
    }

    companion object : InternalEmbraceLogger.LoggerAction {

        @JvmField
        val logger = InternalEmbraceLogger()

        @JvmStatic
        inline fun logDeveloper(className: String, msg: String, throwable: Throwable) {
            log("[$className] $msg", Severity.DEVELOPER, throwable, true)
        }

        @JvmStatic
        inline fun logDeveloper(className: String, msg: String) {
            log("[$className] $msg", Severity.DEVELOPER, null, true)
        }

        @JvmStatic
        @JvmOverloads
        inline fun logDebug(msg: String, throwable: Throwable? = null) {
            log(msg, Severity.DEBUG, throwable, true)
        }

        @JvmStatic
        inline fun logInfo(msg: String) {
            log(msg, Severity.INFO, null, true)
        }

        @JvmStatic
        @JvmOverloads
        inline fun logWarning(msg: String, throwable: Throwable? = null) {
            log(msg, Severity.WARNING, throwable, true)
        }

        @JvmStatic
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

        /**
         * Logs a message.
         *
         * @param msg the message to log.
         * @param severity how severe the log is. If it's lower than the threshold, the message will not be logged.
         * @param throwable exception, if any.
         * @param logStacktrace should add the throwable to the logging
         */

        @JvmStatic
        override fun log(
            msg: String,
            severity: Severity,
            throwable: Throwable?,
            logStacktrace: Boolean
        ) =
            logger.log(msg, severity, throwable, logStacktrace)

        @JvmStatic
        fun setThreshold(severity: Severity) = logger.setThreshold(severity)
    }
}
