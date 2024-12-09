package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.Provider

class FakeEmbLogger(
    var throwOnInternalError: Boolean = true,
    override var errorHandlerProvider: Provider<InternalErrorHandler?> = { null },
) : EmbLogger {

    data class LogMessage(
        val msg: String,
        val throwable: Throwable?,
    )

    var infoMessages: MutableList<LogMessage> = mutableListOf()
    var errorMessages: MutableList<LogMessage> = mutableListOf()
    var sdkNotInitializedMessages: MutableList<LogMessage> = mutableListOf()
    var internalErrorMessages: MutableList<LogMessage> = mutableListOf()

    override fun logInfo(msg: String, throwable: Throwable?) {
        infoMessages.add(LogMessage(msg, throwable))
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
        errorHandlerProvider()?.trackInternalError(type, throwable)
    }
}
