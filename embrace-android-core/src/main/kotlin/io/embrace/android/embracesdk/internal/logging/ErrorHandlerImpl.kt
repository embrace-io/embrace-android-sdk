package io.embrace.android.embracesdk.internal.logging

import android.util.Log
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implementation of [ErrorHandler] that logs to Android logcat & buffers SDK errors until they can be sent
 * to our internaltelemetry.
 */
class ErrorHandlerImpl(
    private val clock: Clock = NormalizedIntervalClock(),
    private val limit: Int = 10,
) : ErrorHandler {

    private class BufferedError(val timestampMs: Long, val throwable: Throwable)

    private val buffer = ConcurrentLinkedQueue<BufferedError>()
    private val count = AtomicInteger(0)

    @Volatile
    private var sink: ((Throwable, Long) -> Unit)? = null

    @Volatile
    private var enabled = true

    override fun log(msg: String, throwable: Throwable?, severity: ErrorHandler.Severity) {
        when (severity) {
            ErrorHandler.Severity.INFO -> Log.i(EMBRACE_TAG, msg, throwable)
            ErrorHandler.Severity.WARNING -> Log.w(EMBRACE_TAG, msg, throwable)
            ErrorHandler.Severity.ERROR -> Log.e(EMBRACE_TAG, msg, throwable)
        }
    }

    override fun <T> guardSdkCode(type: InternalErrorType, default: T, action: () -> T): T = try {
        action()
    } catch (exc: Throwable) {
        report(type, exc)
        default
    }

    override fun <T> guardSdkCodeEach(type: InternalErrorType, items: Iterable<T>, action: (T) -> Unit) {
        try {
            items.forEach { item -> guardSdkCode(type, Unit) { action(item) } }
        } catch (exc: ConcurrentModificationException) {
            report(type, exc)
        }
    }

    override fun <T> guardUserCode(action: () -> T): T = try {
        action()
    } catch (exc: UserCodeException) {
        throw exc
    } catch (exc: Throwable) {
        throw UserCodeException(exc)
    }

    private fun report(type: InternalErrorType, exc: Throwable) {
        when {
            type == InternalErrorType.Ignored -> Unit
            exc is UserCodeException -> log("App-supplied code threw an exception ($type)", exc.cause)
            else -> trackSdkError(type, exc)
        }
    }

    private fun trackSdkError(type: InternalErrorType, throwable: Throwable) {
        runCatching {
            if (!type.shouldCapture() || !enabled || count.incrementAndGet() > limit) {
                return
            }
            sink?.invoke(throwable, clock.now()) ?: buffer.add(BufferedError(clock.now(), throwable))
        }
    }

    override fun drain(sink: ((Throwable, Long) -> Unit)?) {
        if (sink == null) {
            close()
            return
        }
        if (!enabled) {
            return
        }
        this.sink = sink
        while (true) {
            val error = buffer.poll() ?: return
            try {
                sink(error.throwable, error.timestampMs)
            } catch (ignored: Throwable) {
            }
        }
    }

    override fun resetLimit() {
        count.set(0)
    }

    override fun close() {
        enabled = false
        sink = null
        buffer.clear()
    }
}
