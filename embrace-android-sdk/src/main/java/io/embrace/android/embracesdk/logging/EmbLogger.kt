package io.embrace.android.embracesdk.logging

/**
 * A simple interface that is used within the Embrace SDK for logging.
 */
internal interface EmbLogger {

    var internalErrorService: InternalErrorService?

    /**
     * Logs a debug message with an optional throwable.
     */
    fun logDebug(msg: String, throwable: Throwable? = null)

    /**
     * Logs an informational message.
     */
    fun logInfo(msg: String)

    /**
     * Logs a warning message with an optional throwable.
     */
    fun logWarning(msg: String, throwable: Throwable? = null, logStacktrace: Boolean = false)

    /**
     * Logs a warning message with an optional error.
     */
    fun logError(msg: String, throwable: Throwable? = null, logStacktrace: Boolean = false)

    /**
     * Logs a warning message that the SDK is not yet initialized for the given action.
     */
    fun logSdkNotInitialized(action: String)
}
