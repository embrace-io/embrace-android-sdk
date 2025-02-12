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
    fun info(msg: String, throwable: Throwable? = null) = logIfAllowed(
        LogLevel.INFO,
        msg,
        throwable
    )

    /**
     * Log a message with WARN severity.
     */
    fun warn(msg: String, throwable: Throwable? = null) = logIfAllowed(
        LogLevel.WARN,
        msg,
        throwable
    )

    /**
     * Log a message with ERROR severity.
     */
    fun error(msg: String, throwable: Throwable? = null) = logIfAllowed(
        LogLevel.ERROR,
        msg,
        throwable
    )

    /**
     * Logs the message only if the current Embrace log level allows it.
     */
    private fun logIfAllowed(
        gradleLevel: LogLevel,
        msg: String,
        throwable: Throwable?
    ) {
        if (gradleLevel.shouldLog()) {
            gradleLogger.log(gradleLevel, "$logPrefix $msg", throwable)
        }
    }

    companion object {
        private val errorLevels = setOf(LogLevel.ERROR)
        private val warnLevels = errorLevels + LogLevel.WARN
        private val infoLevels = warnLevels + LogLevel.INFO

        @Volatile
        private var levelsToLog: Set<LogLevel> = emptySet()

        fun getSupportedLogLevel(logLevelString: String?): LogLevel? = infoLevels.firstOrNull {
            it.name.equals(logLevelString, true)
        }

        fun setPluginLogLevel(logLevel: LogLevel?) {
            levelsToLog = when (logLevel) {
                LogLevel.INFO -> infoLevels
                LogLevel.WARN -> warnLevels
                LogLevel.ERROR -> errorLevels
                else -> emptySet()
            }
        }

        private fun LogLevel.shouldLog(): Boolean = levelsToLog.contains(this)
    }
}
