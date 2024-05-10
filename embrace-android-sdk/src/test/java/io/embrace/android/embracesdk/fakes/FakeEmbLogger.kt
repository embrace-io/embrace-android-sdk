package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.InternalErrorService

internal class FakeEmbLogger : EmbLogger {

    data class LogMessage(
        val msg: String,
        val throwable: Throwable?,
        val logStacktrace: Boolean
    )

    var debugMessages: MutableList<LogMessage> = mutableListOf()
    var infoMessages: MutableList<LogMessage> = mutableListOf()
    var warningMessages: MutableList<LogMessage> = mutableListOf()
    var errorMessages: MutableList<LogMessage> = mutableListOf()
    var sdkNotInitializedMessages: MutableList<LogMessage> = mutableListOf()
    var internalErrorMessages: MutableList<LogMessage> = mutableListOf()

    override var internalErrorService: InternalErrorService? = null

    override fun logDebug(msg: String, throwable: Throwable?) {
        debugMessages.add(LogMessage(msg, throwable, false))
    }

    override fun logInfo(msg: String) {
        infoMessages.add(LogMessage(msg, null, false))
    }

    override fun logWarning(msg: String, throwable: Throwable?, logStacktrace: Boolean) {
        warningMessages.add(LogMessage(msg, throwable, logStacktrace))
    }

    override fun logError(msg: String, throwable: Throwable?, logStacktrace: Boolean) {
        errorMessages.add(LogMessage(msg, throwable, logStacktrace))
    }

    override fun logSdkNotInitialized(action: String) {
        sdkNotInitializedMessages.add(LogMessage(action, null, false))
    }

    override fun trackInternalError(msg: String, throwable: Throwable, severity: EmbLogger.Severity) {
        internalErrorMessages.add(LogMessage(msg, throwable, false))
    }
}
