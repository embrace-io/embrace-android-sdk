package io.embrace.android.embracesdk.internal.logging

import java.util.concurrent.atomic.AtomicReference

/**
 * Implementation of [ErrorHandler] that logs with [logcat] & buffers SDK errors until they can be sent
 * to our internal telemetry. [now] supplies the time in milliseconds that an error was captured.
 *
 * [beforeBuffering] runs after an error is found to need buffering and just before it is buffered. Only set
 * for testing purposes.
 */
class ErrorHandlerImpl(
    private val now: () -> Long,
    private val logcat: (ErrorHandler.Severity, String, Throwable?) -> Unit,
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

    /**
     * Replaced on [resetLimit] for each session part. An error racing the reset may count
     * against the previous part.
     */
    @Volatile
    private var limiter = SdkErrorLimiter(limit)

    /**
     * Set while the current thread is in the sink. Errors raised by the sink itself are
     * not recorded directly, but the first one is held in [sinkFailure].
     */
    private val inSink = ThreadLocal<Boolean>()

    /**
     * An error the sink raised on the current thread whilst attempting to track an SDK error. This is
     * recorded as [SdkErrorType.ErrorSinkFail] once the sink returns but is only attempted once so
     * failing again in the same way can't recurse.
     */
    private val sinkFailure = ThreadLocal<Throwable>()

    override fun log(msg: String, throwable: Throwable?, severity: ErrorHandler.Severity) {
        runSafely("Failed to log message", logcat) {
            logcat(severity, msg, throwable)
        }
    }

    override fun trackSdkError(type: SdkErrorType, throwable: Throwable) {
        runSafely("Failed to track SDK error", logcat) {
            report(type, throwable)
        }
    }

    private fun report(type: SdkErrorType, exc: Throwable) {
        when {
            type == SdkErrorType.Ignored -> Unit
            exc is UserCodeException -> log("App-supplied code threw an exception ($type)", exc.cause)
            else -> capture(type, exc)
        }
    }

    private fun capture(type: SdkErrorType, throwable: Throwable) {
        if (!type.shouldCapture() || handlerState.get() === HandlerState.Closed) {
            return
        }
        if (inSink.get() == true) {
            holdSinkFailure(throwable)
            return
        }
        if (!limiter.tryAcquire(type, throwable)) {
            return
        }
        val timestampMs = now()

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
                    record(state.recorder, type, throwable, timestampMs)
                    return
                }

                // Still buffering: push the error on top of what is buffered so far
                is HandlerState.Buffering -> {
                    beforeBuffering()
                    if (handlerState.compareAndSet(state, HandlerState.Buffered(type, timestampMs, throwable, state))) {
                        return
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
        runSafely("Failed to drain SDK errors", logcat) {
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
    }

    override fun resetLimit() {
        runSafely("Failed to reset SDK error limit", logcat) {
            limiter = SdkErrorLimiter(limit)
        }
    }

    override fun close() {
        runSafely("Failed to close error handler", logcat) {
            handlerState.set(HandlerState.Closed)
        }
    }

    /**
     * Records the errors buffered up to [newest], oldest first, with the [sink] the drain was invoked with.
     */
    private fun recordBufferedErrors(newest: HandlerState.Buffered, sink: ErrorRecorder) {
        generateSequence(newest) { it.previous as? HandlerState.Buffered }.toList().asReversed().forEach { error ->
            record(sink, error.type, error.throwable, error.timestampMs)
        }
    }

    /**
     * Records an error with [sink]. A sink failure is logged, and doesn't stop later errors from being recorded. The first
     * error the sink raises is then recorded with [sink] as [SdkErrorType.ErrorSinkFail], and anything raised by that
     * attempt is logged and dropped.
     */
    private fun record(
        sink: ErrorRecorder,
        type: SdkErrorType,
        throwable: Throwable,
        timestampMs: Long,
    ) {
        inSink.set(true)
        try {
            runSafely("Failed to record SDK error ($type)", logcat) {
                try {
                    sink(type, throwable, timestampMs)
                } catch (exc: Throwable) {
                    holdSinkFailure(exc)
                    throw exc
                }
            }
            val failure = sinkFailure.get() ?: return
            runSafely("Failed to record SDK error (${SdkErrorType.ErrorSinkFail})", logcat) {
                if (limiter.tryAcquire(SdkErrorType.ErrorSinkFail, failure)) {
                    sink(SdkErrorType.ErrorSinkFail, failure, now())
                }
            }
        } finally {
            inSink.remove()
            sinkFailure.remove()
        }
    }

    private fun holdSinkFailure(exc: Throwable) {
        if (sinkFailure.get() == null) {
            sinkFailure.set(exc)
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
         * Buffering: [type], [timestampMs] and [throwable] are the newest buffered error, and [previous] is what was buffered
         * before it.
         */
        class Buffered(
            val type: SdkErrorType,
            val timestampMs: Long,
            val throwable: Throwable,
            val previous: Buffering,
        ) : Buffering()

        /**
         * The handler is ready to record errors directly with [recorder], the one the drain was invoked with, without going
         * through the buffer.
         */
        class Recording(val recorder: ErrorRecorder) : HandlerState()

        /**
         * The handler is closed and any new errors will be discarded.
         */
        object Closed : HandlerState()
    }

    private inline fun runSafely(
        msg: String,
        logcat: (ErrorHandler.Severity, String, Throwable?) -> Unit,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (exc: Throwable) {
            try {
                logcat(ErrorHandler.Severity.WARNING, msg, exc)
            } catch (_: Throwable) {
                // no-op
            }
        }
    }
}
