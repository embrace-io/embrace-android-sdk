package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

public class FakeEmbLogger : EmbLogger {

    public data class LogMessage(
        val msg: String,
        val throwable: Throwable?
    )

    public var debugMessages: MutableList<LogMessage> = mutableListOf()
    public var infoMessages: MutableList<LogMessage> = mutableListOf()
    public var warningMessages: MutableList<LogMessage> = mutableListOf()
    public var errorMessages: MutableList<LogMessage> = mutableListOf()
    public var sdkNotInitializedMessages: MutableList<LogMessage> = mutableListOf()
    public var internalErrorMessages: MutableList<LogMessage> = mutableListOf()

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
