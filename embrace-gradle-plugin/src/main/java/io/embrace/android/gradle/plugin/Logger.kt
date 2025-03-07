package io.embrace.android.gradle.plugin

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging

/**
 * General logging utility class to be used within the plugin.
 */
class Logger<T>(clazz: Class<T>) {
    private val gradleLogger: org.gradle.api.logging.Logger = Logging.getLogger(clazz)
    private val logPrefix = "[EmbraceGradlePlugin] [${clazz.simpleName}]"

    /**
     * Log a message with INFO severity.
     */
    fun info(msg: String, throwable: Throwable? = null) = log(
        LogLevel.INFO,
        msg,
        throwable
    )

    /**
     * Log a message with WARN severity.
     */
    fun warn(msg: String, throwable: Throwable? = null) = log(
        LogLevel.WARN,
        msg,
        throwable
    )

    /**
     * Log a message with ERROR severity.
     */
    fun error(msg: String, throwable: Throwable? = null) = log(
        LogLevel.ERROR,
        msg,
        throwable
    )

    /**
     * Logs the message only if the current Embrace log level allows it.
     */
    private fun log(
        gradleLevel: LogLevel,
        msg: String,
        throwable: Throwable?,
    ) {
        gradleLogger.log(gradleLevel, "$logPrefix $msg", throwable)
    }
}
