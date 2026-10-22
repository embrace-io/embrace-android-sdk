package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.ErrorRecorder
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.UserCodeException

/**
 * Records log messages & SDK errors. If [delegate] is supplied, guarded code and SDK errors are forwarded to it
 * so that buffering & delivery behave like production.
 */
class FakeErrorHandler(
    var throwOnSdkError: Boolean = true,
    val ignoredErrors: List<SdkErrorType> = emptyList(),
    private val delegate: ErrorHandler? = null,
) : ErrorHandler {

    data class LogMessage(
        val msg: String,
        val throwable: Throwable?,
    )

    var infoMessages: MutableList<LogMessage> = mutableListOf()
    var warningMessages: MutableList<LogMessage> = mutableListOf()
    var errorMessages: MutableList<LogMessage> = mutableListOf()
    var sdkErrorMessages: MutableList<LogMessage> = mutableListOf()

    override fun log(msg: String, throwable: Throwable?, severity: ErrorHandler.Severity) {
        val messages = when (severity) {
            ErrorHandler.Severity.INFO -> infoMessages
            ErrorHandler.Severity.WARNING -> warningMessages
            ErrorHandler.Severity.ERROR -> errorMessages
        }
        messages.add(LogMessage(msg, throwable))
    }

    override fun trackSdkError(type: SdkErrorType, throwable: Throwable) {
        when {
            type == SdkErrorType.Ignored || ignoredErrors.contains(type) -> return
            throwable is UserCodeException -> log("App-supplied code threw an exception ($type)", throwable.cause)
            throwOnSdkError -> throw IllegalStateException("SDK error: $type", throwable)
            else -> sdkErrorMessages.add(LogMessage(type.toString(), throwable))
        }
        delegate?.trackSdkError(type, throwable)
    }

    override fun drain(sink: ErrorRecorder?) {
        delegate?.drain(sink)
    }

    override fun resetLimit() {
        delegate?.resetLimit()
    }

    override fun close() {
        delegate?.close()
    }
}
