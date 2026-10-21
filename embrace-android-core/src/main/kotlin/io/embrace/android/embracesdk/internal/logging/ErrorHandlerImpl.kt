package io.embrace.android.embracesdk.internal.logging

import android.util.Log
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal const val EMBRACE_TAG = "[Embrace]"

/**
 * Implementation of [ErrorHandler] that logs to Android logcat & buffers SDK errors until they can be sent
 * to our internal telemetry.
 *
 * [beforeBuffering] runs after an error is found to need buffering and just before it is buffered. Only set
 * for testing purposes.
 */
class ErrorHandlerImpl(
    private val clock: Clock = NormalizedIntervalClock(),
    private val limit: Int = 10,
    private val beforeBuffering: () -> Unit = {},
) : ErrorHandler {
    private val handlerState = AtomicReference<HandlerState>(HandlerState.Buffering(emptyList()))
    private val count = AtomicInteger(0)

    // Set by drain before it switches the state to Recording, so a thread capturing an error can send it directly
    // rather than add to a buffer that shouldn't be added to.
    @Volatile
    private var recordError: ((Throwable, Long) -> Unit)? = null

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
            if (!type.shouldCapture() || handlerState.get() === HandlerState.Closed || count.incrementAndGet() > limit) {
                return
            }
            val timestampMs = clock.now()
            while (true) {
                when (val state = handlerState.get()) {
                    // Drop the error if the handler is closed
                    HandlerState.Closed -> return

                    // Once the handler is recording, record the error directly rather than buffering it
                    HandlerState.Recording -> {
                        recordError?.invoke(throwable, timestampMs)
                        return
                    }

                    // Buffer the error by swapping in a Buffering whose list is the one read above plus this error. The swap
                    // fails if another thread changed the state since then (buffered another error, drained or closed), and
                    // the loop retries against the new state, so every error is either buffered, recorded or dropped by a
                    // close.
                    is HandlerState.Buffering -> {
                        beforeBuffering()
                        val errors = state.bufferedErrors + BufferedError(timestampMs, throwable)
                        if (handlerState.compareAndSet(state, HandlerState.Buffering(errors))) {
                            return
                        }
                    }
                }
            }
        }
    }

    override fun drain(sink: ((Throwable, Long) -> Unit)?) {
        if (sink == null) {
            close()
            return
        }
        recordError = sink
        while (true) {
            val current = handlerState.get()
            if (current === HandlerState.Closed) {
                return
            }

            // Swap whatever was read for Recording, taking the buffered errors with it: from then on no thread can add to
            // that buffer, and new errors bypass the buffer and are recorded directly. The swap fails if another thread
            // changed the state since it was read (buffered another error or closed), and the loop retries against the
            // new state. Once it succeeds, the errors it took are recorded and the drain returns.
            if (handlerState.compareAndSet(current, HandlerState.Recording)) {
                (current as? HandlerState.Buffering)?.let { recordBufferedErrors(it.bufferedErrors, sink) }
                return
            }
        }
    }

    override fun resetLimit() {
        count.set(0)
    }

    override fun close() {
        handlerState.set(HandlerState.Closed)
        recordError = null
    }

    /**
     * Records the buffered [errors], oldest first, with the [sink] the drain was invoked with.
     */
    private fun recordBufferedErrors(errors: List<BufferedError>, sink: (Throwable, Long) -> Unit) {
        errors.forEach { error ->
            try {
                sink(error.throwable, error.timestampMs)
            } catch (_: Throwable) {
                // A failure to record one error doesn't stop the rest from being recorded
            }
        }
    }

    /**
     * The state of the error handler that decides what happens with a new error that's captured
     */
    private sealed class HandlerState {
        /**
         * The handler is not ready to record errors, and incoming ones are buffered in [bufferedErrors].
         */
        class Buffering(val bufferedErrors: List<BufferedError>) : HandlerState()

        /**
         * The handler is ready to record errors directly without going through the buffer.
         */
        object Recording : HandlerState()

        /**
         * The handler is closed and any new errors will be discarded.
         */
        object Closed : HandlerState()
    }

    private class BufferedError(val timestampMs: Long, val throwable: Throwable)
}
