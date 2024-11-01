package io.embrace.android.embracesdk.internal.logging

/**
 * A simple interface that is used within the Embrace SDK for logging.
 */
interface EmbLogger : InternalErrorHandler {

    enum class Severity {
        DEBUG, INFO, WARNING, ERROR
    }

    /**
     * Logs an informational message.
     */
    fun logInfo(msg: String, throwable: Throwable? = null)

    /**
     * Logs a warning message with an optional error.
     */
    fun logError(msg: String, throwable: Throwable? = null)

    /**
     * Logs a warning message that the SDK is not yet initialized for the given action.
     */
    fun logSdkNotInitialized(action: String)
}
