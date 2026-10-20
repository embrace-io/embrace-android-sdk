package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
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
        if (!ignoredErrors.contains(type)) {
            if (throwOnSdkError) {
                throw IllegalStateException("SDK error: $type", throwable)
            } else {
                sdkErrorMessages.add(LogMessage(type.toString(), throwable))
                delegate?.trackSdkError(type, throwable)
            }
        }
    }

    override fun <T> guardSdkCode(type: SdkErrorType, default: T, action: () -> T): T = try {
        action()
    } catch (exc: Throwable) {
        when {
            type == SdkErrorType.Ignored -> Unit
            exc is UserCodeException -> log("App-supplied code threw an exception ($type)", exc.cause)
            else -> sdkErrorMessages.add(LogMessage(type.toString(), exc))
        }
        delegate?.guardSdkCode(type) { throw exc }
        default
    }

    override fun <T> guardSdkCodeEach(type: SdkErrorType, items: Iterable<T>, action: (T) -> Unit) {
        try {
            items.forEach { item -> guardSdkCode(type) { action(item) } }
        } catch (exc: ConcurrentModificationException) {
            guardSdkCode(type) { throw exc }
        }
    }

    override fun <T> guardUserCode(action: () -> T): T {
        return try {
            action()
        } catch (exc: UserCodeException) {
            throw exc
        } catch (exc: Throwable) {
            throw UserCodeException(exc)
        }
    }

    override fun drain(sink: ((Throwable, Long) -> Unit)?) {
        delegate?.drain(sink)
    }

    override fun resetLimit() {
        delegate?.resetLimit()
    }

    override fun close() {
        delegate?.close()
    }
}
