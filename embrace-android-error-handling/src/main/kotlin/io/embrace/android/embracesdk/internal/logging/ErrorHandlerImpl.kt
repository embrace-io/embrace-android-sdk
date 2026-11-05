package io.embrace.android.embracesdk.internal.logging

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

private const val CLOSE_POLL_NS = 100_000L

/**
 * Implementation of [ErrorHandler] that logs with [logcat] & buffers SDK errors until they can be sent
 * to our internal telemetry. [now] supplies the time in milliseconds that an error was captured.
 *
 * Nothing thrown escapes the handler. A failure within it is reported as [SdkErrorType.ErrorSinkFail].
 * That covers the sink throwing, an error tracked on the same thread while the sink runs.
 *
 * [beforeBuffering] runs after an error is found to need buffering and just before the capacity check. [beforeStep] runs
 * before the `"drain"`, `"buffer"` and `"record"` steps. Only set for testing purposes.
 */
class ErrorHandlerImpl(
    private val now: () -> Long,
    private val logcat: (ErrorHandler.Severity, String, Throwable?) -> Unit,
    private val limit: Int = 10,
    private val closeTimeoutMs: Long = 500,
    private val beforeBuffering: () -> Unit = {},
    private val beforeStep: (String) -> Unit = {},
) : ErrorHandler {

    /**
     * [HandlerState.Empty] -> [HandlerState.Buffered] -> [HandlerState.Recording]
     * [close] -> [HandlerState.Closed].
     */
    private val handlerState = AtomicReference<HandlerState>(HandlerState.Empty)

    /**
     * Replaced on [resetLimit] for each session part. An error racing the reset may count against the previous part.
     */
    @Volatile
    private var limiter = SdkErrorLimiter(limit)

    /**
     * In-progress [record] calls
     */
    private val recordsInFlight = AtomicInteger(0)

    /**
     * Set while the current thread is in the sink. Errors raised by the sink itself are
     * not recorded directly, but the first one is held in [sinkFailure].
     */
    private val inSink = ThreadLocal<Boolean>()

    /**
     * The first error raised on the current thread while [record] runs, snapshotted as [SdkErrorType.ErrorSinkFail]. It is
     * recorded once the sink returns, unless the snapshot being recorded was itself an [SdkErrorType.ErrorSinkFail], in
     * which case it is dropped. Either way it is only attempted once, so failing again in the same way can't recurse.
     */
    private val sinkFailure = ThreadLocal<SdkErrorSnapshot>()

    override fun log(msg: String, throwable: Throwable?, severity: ErrorHandler.Severity) {
        runSafely({ "Failed to log message" }, logcat) {
            logcat(severity, msg, throwable)
        }
    }

    override fun trackSdkError(type: SdkErrorType, throwable: Throwable) {
        entryPoint("Failed to track SDK error", throwable) {
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
        val snapshot = try {
            ThrowableSnapshotter.snapshot(type, throwable, safeNow())
        } catch (_: OutOfMemoryError) {
            return
        }
        if (inSink.get() == true) {
            if (sinkFailure.get() == null) {
                sinkFailure.set(snapshot.copy(type = SdkErrorType.ErrorSinkFail))
            } else {
                logSafely(ErrorHandler.Severity.WARNING, "Dropped SDK error tracked whilst one is being recorded", throwable)
            }
            return
        }
        val reserved = when (bufferRoomFor(snapshot)) {
            BufferRoom.NONE -> return
            BufferRoom.RESERVED -> true
            BufferRoom.OPEN -> false
        }
        val limiter = limiter
        if (!reserved && !limiter.tryAcquire(snapshot)) {
            return
        }
        var kept = false
        try {
            kept = bufferOrRecord(snapshot, reserved)
        } finally {
            if (!kept && !reserved) {
                limiter.release(snapshot)
            }
        }
    }

    /**
     * Checked before taking a limiter slot, so a full buffer doesn't spend the part's limit.
     */
    private fun bufferRoomFor(snapshot: SdkErrorSnapshot): BufferRoom {
        val state = handlerState.get() as? HandlerState.Buffering ?: return BufferRoom.OPEN
        beforeBuffering()
        return when {
            state.size < limit -> BufferRoom.OPEN
            snapshot.type == SdkErrorType.ErrorSinkFail && state.size == limit -> BufferRoom.RESERVED
            else -> BufferRoom.NONE
        }
    }

    private enum class BufferRoom { OPEN, RESERVED, NONE }

    /**
     * Returns false if [snapshot] was dropped.
     */
    private fun bufferOrRecord(snapshot: SdkErrorSnapshot, reserved: Boolean): Boolean {
        // The buffer is a Treiber stack on a HandlerState instance that indicates that it is buffering errors. An error
        // is added by swapping in a new Buffered instance that contains the latest error and links back to the last one.
        // The swap retries if another thread changed the HandlerState since it was read, and that retry will proceed
        // according to what the new state is. So when it says the handler is no longer buffering, it will either
        // record the error directly or drop it, bypassing the buffer either way since it's no longer buffering.
        while (true) {
            when (val state = handlerState.get()) {
                // Drop the error if the handler is closed
                HandlerState.Closed -> return false

                // Once the handler is recording, record the error directly rather than buffering it
                is HandlerState.Recording -> {
                    if (!reserved || limiter.tryAcquire(snapshot)) {
                        record(state.recorder, snapshot)
                    }
                    return true
                }

                // Still buffering: push the error on top of what is buffered so far, or drop it if the buffer is full
                is HandlerState.Buffering -> {
                    val capacity = if (snapshot.type == SdkErrorType.ErrorSinkFail) limit + 1 else limit
                    if (state.size >= capacity) {
                        return false
                    }
                    beforeStep("buffer")
                    if (handlerState.compareAndSet(state, HandlerState.Buffered(snapshot, state))) {
                        return true
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
        entryPoint("Failed to drain SDK errors") {
            try {
                drainOnce(sink)
            } catch (exc: Throwable) {
                drainOnce(sink)
                throw exc
            }
        }
    }

    /**
     * If this fails before taking the buffer, the buffer is kept for a later drain.
     */
    private fun drainOnce(sink: ErrorRecorder) {
        beforeStep("drain")
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
        entryPoint("Failed to reset SDK error limit") {
            limiter = SdkErrorLimiter(limit)
        }
    }

    override fun close() {
        entryPoint("Failed to close error handler") {
            if (handlerState.getAndSet(HandlerState.Closed) !== HandlerState.Closed) {
                awaitRecordsInFlight()
            }
        }
    }

    private fun awaitRecordsInFlight() {
        val own = if (inSink.get() == true) 1 else 0
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(closeTimeoutMs)
        while (recordsInFlight.get() > own) {
            val remainingNs = deadline - System.nanoTime()
            if (remainingNs <= 0 || Thread.currentThread().isInterrupted) {
                logSafely(ErrorHandler.Severity.WARNING, "Closed error handler with SDK errors still being recorded", null)
                return
            }
            LockSupport.parkNanos(minOf(remainingNs, CLOSE_POLL_NS))
        }
    }

    /**
     * Records the errors buffered up to [newest], oldest first, with the [sink] the drain was invoked with.
     */
    private fun recordBufferedErrors(newest: HandlerState.Buffered, sink: ErrorRecorder) {
        (newest.previous as? HandlerState.Buffered)?.let { recordBufferedErrors(it, sink) }
        record(sink, newest.snapshot)
    }

    /**
     * Records [snapshot] with [sink]. A throw from the sink is logged and doesn't stop later errors being recorded. The first
     * error raised while the sink runs, thrown or tracked, is then recorded as [SdkErrorType.ErrorSinkFail], subject to the
     * limit. Anything raised while recording an [SdkErrorType.ErrorSinkFail], whether that follow-up or a [snapshot] already
     * of that type, is dropped; a throw is still logged. Nothing is recorded once the handler is closed.
     */
    private fun record(sink: ErrorRecorder, snapshot: SdkErrorSnapshot) {
        recordsInFlight.incrementAndGet()
        try {
            beforeStep("record")
            inSink.set(true)
            if (handlerState.get() === HandlerState.Closed) {
                return
            }
            if (snapshot.type == SdkErrorType.ErrorSinkFail) {
                holdSinkFailure(snapshot)
                runSafely({ "Failed to record SDK error (${snapshot.type})" }, logcat) {
                    sink(snapshot)
                }
                return
            }
            runSafely({ "Failed to record SDK error (${snapshot.type})" }, logcat) {
                try {
                    sink(snapshot)
                } catch (exc: Throwable) {
                    try {
                        holdSinkFailure(ThrowableSnapshotter.snapshot(SdkErrorType.ErrorSinkFail, exc, safeNow()))
                    } catch (_: Throwable) {
                        // no-op: a failure while handling a sink failure is not reported
                    }
                    throw exc
                }
            }
            val failure = sinkFailure.get() ?: return
            if (handlerState.get() === HandlerState.Closed) {
                return
            }
            runSafely({ "Failed to record SDK error (${SdkErrorType.ErrorSinkFail})" }, logcat) {
                if (limiter.tryAcquire(failure)) {
                    sink(failure)
                }
            }
        } finally {
            inSink.remove()
            sinkFailure.remove()
            recordsInFlight.decrementAndGet()
        }
    }

    private fun holdSinkFailure(snapshot: SdkErrorSnapshot) {
        if (sinkFailure.get() == null) {
            sinkFailure.set(snapshot)
        }
    }

    /**
     * The state of the error handler that decides what happens with a new error that's captured.
     */
    private sealed class HandlerState {
        /**
         * The handler is not ready to record errors and is buffering them.
         */
        sealed class Buffering : HandlerState() {
            abstract val size: Int
        }

        /**
         * Buffering, with nothing buffered yet.
         */
        object Empty : Buffering() {
            override val size: Int = 0
        }

        /**
         * Buffering: [snapshot] is the newest buffered error, and [previous] is what was buffered before it.
         */
        class Buffered(
            val snapshot: SdkErrorSnapshot,
            val previous: Buffering,
        ) : Buffering() {
            override val size: Int = previous.size + 1
        }

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

    private inline fun entryPoint(msg: String, tracked: Throwable? = null, action: () -> Unit) {
        try {
            action()
        } catch (exc: Throwable) {
            try {
                logSafely(ErrorHandler.Severity.WARNING, msg, exc)
                if (tracked != null) {
                    logSafely(ErrorHandler.Severity.WARNING, "$msg: tracked error", tracked)
                }
                capture(SdkErrorType.ErrorSinkFail, exc)
            } catch (_: Throwable) {
                // no-op
            }
        }
    }

    private fun safeNow(): Long = try {
        now()
    } catch (_: Throwable) {
        try {
            System.currentTimeMillis()
        } catch (_: Throwable) {
            0L
        }
    }

    private fun logSafely(severity: ErrorHandler.Severity, msg: String, throwable: Throwable?) {
        try {
            logcat(severity, msg, throwable)
        } catch (_: Throwable) {
            // no-op
        }
    }

    private inline fun runSafely(
        msg: () -> String,
        logcat: (ErrorHandler.Severity, String, Throwable?) -> Unit,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (exc: Throwable) {
            try {
                logcat(ErrorHandler.Severity.WARNING, msg(), exc)
            } catch (_: Throwable) {
                // no-op
            }
        }
    }
}
