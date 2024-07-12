package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorHandler
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.EmbLogger

internal class FakeEmbLogger : EmbLogger {

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

    override var internalErrorService: InternalErrorHandler? = null

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
        sdkNotInitializedMessages.add(LogMessage(action, null))
    }

    override fun trackInternalError(type: InternalErrorType, throwable: Throwable) {
        internalErrorMessages.add(LogMessage(type.toString(), throwable))
    }
}
