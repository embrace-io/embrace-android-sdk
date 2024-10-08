package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

class FakeEmbLogger(
    var throwOnInternalError: Boolean = true
) : EmbLogger {

    data class LogMessage(
        val msg: String,
        val throwable: Throwable?
    )

    var debugMessages: MutableList<LogMessage> = mutableListOf()
    var infoMessages: MutableList<LogMessage> = mutableListOf()
    var warningMessages: MutableList<LogMessage> = mutableListOf()
    var errorMessages: MutableList<LogMessage> = mutableListOf()
    var sdkNotInitializedMessages: MutableList<LogMessage> = mutableListOf()
    var internalErrorMessages: MutableList<LogMessage> = mutableListOf()

    override fun logDebug(msg: String, throwable: Throwable?) {
        debugMessages.add(LogMessage(msg, throwable))
    }

    override fun logInfo(msg: String, throwable: Throwable?) {
        infoMessages.add(LogMessage(msg, throwable))
    }

    override fun logWarning(msg: String, throwable: Throwable?) {
        warningMessages.add(LogMessage(msg, throwable))
    }

    override fun logError(msg: String, throwable: Throwable?) {
        errorMessages.add(LogMessage(msg, throwable))
    }

    override fun logSdkNotInitialized(action: String) {
        if (throwOnInternalError) {
            error("SDK not initialized: $action")
        }
        sdkNotInitializedMessages.add(LogMessage(action, null))
    }

    override fun trackInternalError(type: InternalErrorType, throwable: Throwable) {
        if (throwOnInternalError) {
            throw IllegalStateException("Internal error: $type", throwable)
        }
        internalErrorMessages.add(LogMessage(type.toString(), throwable))
    }
}
