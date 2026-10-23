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

    /**
     * The state is [HandlerState.Empty] at handler init. It will transition to [HandlerState.Buffered]
     * when the first error is captured. After that, a new instance of [HandlerState.Buffered] will be
     * set in a lock-free way that contains the new error and a link back to the instance it replaced,
     * all the way back to [HandlerState.Empty].
     *
     * A [drain] will stop the buffering and transition it to [HandlerState.Recording], which
     * means the error should be recorded directly rather than go through the buffer. Meanwhile,
     * a [close] will transition it to [HandlerState.Closed] and all new errors will be rejected.
     */
    private val handlerState = AtomicReference<HandlerState>(HandlerState.Empty)
    private val count = AtomicInteger(0)

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

            // The buffer is a Treiber stack on a HandlerState instance that indicates that it is buffering errors. An error
            // is added by swapping in a new Buffering instance contains the latest error and links back to the last one.
            // The swap retries if another thread changed the HandlerState since it was read, and that retry will proceed
            // according to what the new state is. So when it says the handler is no longer buffering, it will either
            // record the error directly or drop it, bypassing the buffer either way since it's no longer buffering.
            while (true) {
                when (val state = handlerState.get()) {
                    // Drop the error if the handler is closed
                    HandlerState.Closed -> return

                    // Once the handler is recording, record the error directly rather than buffering it
                    is HandlerState.Recording -> {
                        state.record(throwable, timestampMs)
                        return
                    }

                    // Still buffering: push the error on top of what is buffered so far
                    is HandlerState.Buffering -> {
                        beforeBuffering()
                        if (handlerState.compareAndSet(state, HandlerState.Buffered(timestampMs, throwable, state))) {
                            return
                        }
                    }
                }
            }
        }
    }

    override fun drain(sink: ErrorRecorder?) {
        if (sink == null) {
            close()
            return
        }
        val recording = HandlerState.Recording(sink)
        while (true) {
            val current = handlerState.get()
            if (current === HandlerState.Closed) {
                return
            }

            // Swap whatever was read for Recording, taking the buffered errors with it. From this point on, no thread
            // can add to that buffer, and new errors bypass the buffer and are recorded directly if the handler hasn't
            // been closed. The swap fails if another thread changed the state since it was read, and the loop retries
            // against the new state. Once it succeeds, the errors it took are recorded and the drain returns.
            if (handlerState.compareAndSet(current, recording)) {
                (current as? HandlerState.Buffered)?.let { recordBufferedErrors(it, sink) }
                return
            }
        }
    }

    override fun resetLimit() {
        count.set(0)
    }

    override fun close() {
        handlerState.set(HandlerState.Closed)
    }

    /**
     * Records the errors buffered up to [newest], oldest first, with the [sink] the drain was invoked with.
     */
    private fun recordBufferedErrors(newest: HandlerState.Buffered, sink: ErrorRecorder) {
        generateSequence(newest) { it.previous as? HandlerState.Buffered }.toList().asReversed().forEach { error ->
            try {
                sink(error.throwable, error.timestampMs)
            } catch (_: Throwable) {
                // A failure to record one error doesn't stop the rest from being recorded
            }
        }
    }

    /**
     * The state of the error handler that decides what happens with a new error that's captured.
     */
    private sealed class HandlerState {
        /**
         * The handler is not ready to record errors and is buffering them.
         */
        sealed class Buffering : HandlerState()

        /**
         * Buffering, with nothing buffered yet.
         */
        object Empty : Buffering()

        /**
         * Buffering: [timestampMs] and [throwable] are the newest buffered error, and [previous] is what was buffered before it.
         */
        class Buffered(val timestampMs: Long, val throwable: Throwable, val previous: Buffering) : Buffering()

        /**
         * The handler is ready to record errors directly with [recorder], the one the drain was invoked with, without going
         * through the buffer.
         */
        class Recording(private val recorder: ErrorRecorder) : HandlerState() {
            fun record(throwable: Throwable, timestampMs: Long) = recorder(throwable, timestampMs)
        }

        /**
         * The handler is closed and any new errors will be discarded.
         */
        object Closed : HandlerState()
    }
}
