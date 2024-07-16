package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorHandler
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType

/**
 * A simple interface that is used within the Embrace SDK for logging.
 */
@InternalApi
public interface EmbLogger {

    @InternalApi
    public enum class Severity {
        DEBUG, INFO, WARNING, ERROR
    }

    public var internalErrorService: InternalErrorHandler?

    /**
     * Logs a debug message with an optional throwable.
     */
    public fun logDebug(msg: String, throwable: Throwable? = null)

    /**
     * Logs an informational message.
     */
    public fun logInfo(msg: String, throwable: Throwable? = null)

    /**
     * Logs a warning message with an optional throwable.
     */
    public fun logWarning(msg: String, throwable: Throwable? = null)

    /**
     * Logs a warning message with an optional error.
     */
    public fun logError(msg: String, throwable: Throwable? = null)

    /**
     * Logs a warning message that the SDK is not yet initialized for the given action.
     */
    public fun logSdkNotInitialized(action: String)

    /**
     * Tracks an internal error. This is sent to our own telemetry so should be used sparingly
     * & only for states that we can take actions to improve.
     */
    public fun trackInternalError(type: InternalErrorType, throwable: Throwable)
}
