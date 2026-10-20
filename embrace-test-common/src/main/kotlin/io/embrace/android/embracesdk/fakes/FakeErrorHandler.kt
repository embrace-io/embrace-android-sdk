package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.UserCodeException

/**
 * Records log messages & internal errors. If [delegate] is supplied, guarded code and internal errors are forwarded to it
 * so that buffering & delivery behave like production.
 */
class FakeErrorHandler(
    var throwOnInternalError: Boolean = true,
    val ignoredErrors: List<InternalErrorType> = emptyList(),
    private val delegate: ErrorHandler? = null,
) : ErrorHandler {

    data class LogMessage(
        val msg: String,
        val throwable: Throwable?,
    )

    var infoMessages: MutableList<LogMessage> = mutableListOf()
    var warningMessages: MutableList<LogMessage> = mutableListOf()
    var errorMessages: MutableList<LogMessage> = mutableListOf()
    var internalErrorMessages: MutableList<LogMessage> = mutableListOf()

    override fun log(msg: String, throwable: Throwable?, severity: ErrorHandler.Severity) {
        val messages = when (severity) {
            ErrorHandler.Severity.INFO -> infoMessages
            ErrorHandler.Severity.WARNING -> warningMessages
            ErrorHandler.Severity.ERROR -> errorMessages
        }
        messages.add(LogMessage(msg, throwable))
    }

    override fun trackInternalError(type: InternalErrorType, throwable: Throwable) {
        if (!ignoredErrors.contains(type)) {
            if (throwOnInternalError) {
                throw IllegalStateException("Internal error: $type", throwable)
            } else {
                internalErrorMessages.add(LogMessage(type.toString(), throwable))
                delegate?.trackInternalError(type, throwable)
            }
        }
    }

    override fun <T> guardSdkCode(type: InternalErrorType, default: T, action: () -> T): T {
        if (delegate != null) {
            return delegate.guardSdkCode(type, default, action)
        }
        return try {
            action()
        } catch (exc: Throwable) {
            if (type != InternalErrorType.Ignored && exc !is UserCodeException) {
                internalErrorMessages.add(LogMessage(type.toString(), exc))
            }
            default
        }
    }

    override fun <T> guardSdkCodeEach(type: InternalErrorType, items: Iterable<T>, action: (T) -> Unit) {
        if (delegate != null) {
            delegate.guardSdkCodeEach(type, items, action)
        } else {
            items.forEach { item -> guardSdkCode(type, Unit) { action(item) } }
        }
    }

    override fun <T> guardUserCode(action: () -> T): T {
        if (delegate != null) {
            return delegate.guardUserCode(action)
        }
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
