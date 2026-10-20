package io.embrace.android.embracesdk.internal.logging

import android.util.Log
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

internal const val EMBRACE_TAG = "[Embrace]"

/**
 * Implementation of [ErrorHandler] that logs to Android logcat & buffers SDK errors until they can be sent
 * to our internal telemetry.
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

    override fun trackSdkError(type: SdkErrorType, throwable: Throwable) {
        report(type, throwable)
    }

    private fun report(type: SdkErrorType, exc: Throwable) {
        when {
            type == SdkErrorType.Ignored -> Unit
            exc is UserCodeException -> log("App-supplied code threw an exception ($type)", exc.cause)
            else -> capture(type, exc)
        }
    }

    private fun capture(type: SdkErrorType, throwable: Throwable) {
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
