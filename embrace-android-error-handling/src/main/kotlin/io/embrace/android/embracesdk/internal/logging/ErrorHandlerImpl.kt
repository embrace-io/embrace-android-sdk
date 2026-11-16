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
        if (inSink.get() == true) {
            holdTrackedSinkFailure(throwable)
            return
        }
        val reserved = when (bufferRoomFor(type)) {
            BufferRoom.NONE -> return
            BufferRoom.RESERVED -> true
            BufferRoom.OPEN -> false
        }
        val limiter = limiter
        if (!reserved && limiter.exhausted) {
            return
        }
        val key = try {
            ThrowableSnapshotter.keyOf(type, throwable)
        } catch (_: OutOfMemoryError) {
            return
        }
        if (!reserved && !limiter.tryAcquire(key)) {
            return
        }
        var kept = false
        try {
            kept = snapshotAndKeep(type, throwable, key, reserved)
        } finally {
            if (!kept && !reserved) {
                limiter.release(key)
            }
        }
    }

    /**
     * Returns false if the error was dropped.
     */
    private fun snapshotAndKeep(
        type: SdkErrorType,
        throwable: Throwable,
        key: SdkErrorKey,
        reserved: Boolean,
    ): Boolean {
        val snapshot = try {
            ThrowableSnapshotter.snapshot(type, throwable, safeNow())
        } catch (_: OutOfMemoryError) {
            return false
        }
        return bufferOrRecord(snapshot, key, reserved)
    }

    private fun holdTrackedSinkFailure(throwable: Throwable) {
        if (sinkFailure.get() != null) {
            logSafely(ErrorHandler.Severity.WARNING, "Dropped SDK error tracked whilst one is being recorded", throwable)
            return
        }
        try {
            sinkFailure.set(ThrowableSnapshotter.snapshot(SdkErrorType.ErrorSinkFail, throwable, safeNow()))
        } catch (_: OutOfMemoryError) {
            // no-op
        }
    }

    /**
     * Checked before taking a limiter slot, so a full buffer doesn't spend the part's limit.
     */
    private fun bufferRoomFor(type: SdkErrorType): BufferRoom {
        val state = handlerState.get() as? HandlerState.Buffering ?: return BufferRoom.OPEN
        beforeBuffering()
        return when {
            state.size < limit -> BufferRoom.OPEN
            type == SdkErrorType.ErrorSinkFail && state.size == limit -> BufferRoom.RESERVED
            else -> BufferRoom.NONE
        }
    }

    private enum class BufferRoom { OPEN, RESERVED, NONE }

    /**
     * Returns false if [snapshot] was dropped.
     */
    private fun bufferOrRecord(snapshot: SdkErrorSnapshot, key: SdkErrorKey, reserved: Boolean): Boolean {
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
                    if (!reserved || limiter.tryAcquire(key)) {
                        record(snapshot)
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

    override fun drain(sink: SdkErrorSink?) {
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
    private fun drainOnce(sink: SdkErrorSink) {
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
                when (current) {
                    is HandlerState.Buffered -> recordBufferedErrors(current)
                    is HandlerState.Recording -> if (current.recorder !== sink) {
                        awaitRecordsInFlight("Replaced SDK error sink with SDK errors still being recorded")
                        closeSink(current.recorder)
                    }
                    else -> Unit
                }
                return
            }
        }
    }

    override fun flush() {
        entryPoint("Failed to flush SDK errors") {
            if (handlerState.get() is HandlerState.Recording) {
                useSink("flush", { "Failed to flush SDK errors" }) { it.flush() }
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
            val previous = handlerState.getAndSet(HandlerState.Closed)
            if (previous !== HandlerState.Closed) {
                awaitRecordsInFlight("Closed error handler with SDK errors still being recorded")
                (previous as? HandlerState.Recording)?.let { closeSink(it.recorder) }
            }
        }
    }

    private fun awaitRecordsInFlight(timeoutMsg: String) {
        val own = if (inSink.get() == true) 1 else 0
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(closeTimeoutMs)
        while (recordsInFlight.get() > own) {
            val remainingNs = deadline - System.nanoTime()
            if (remainingNs <= 0 || Thread.currentThread().isInterrupted) {
                logSafely(ErrorHandler.Severity.WARNING, timeoutMsg, null)
                return
            }
            LockSupport.parkNanos(minOf(remainingNs, CLOSE_POLL_NS))
        }
    }

    /**
     * A throw is tracked as [SdkErrorType.ErrorSinkFail], which is dropped if the handler is closed.
     */
    private fun closeSink(sink: SdkErrorSink) {
        try {
            sink.close()
        } catch (exc: Throwable) {
            logSafely(ErrorHandler.Severity.WARNING, "Failed to close SDK error sink", exc)
            try {
                capture(SdkErrorType.ErrorSinkFail, exc)
            } catch (_: Throwable) {
                // no-op
            }
        }
    }

    /**
     * Records the errors buffered up to [newest], oldest first.
     */
    private fun recordBufferedErrors(newest: HandlerState.Buffered) {
        (newest.previous as? HandlerState.Buffered)?.let { recordBufferedErrors(it) }
        record(newest.snapshot)
    }

    private fun record(snapshot: SdkErrorSnapshot) {
        val msg = { "Failed to record SDK error (${snapshot.type})" }
        val reporting = snapshot.takeIf { it.type == SdkErrorType.ErrorSinkFail }
        useSink("record", msg, reporting) { it.record(snapshot) }
    }

    /**
     * Runs [action] with the current sink. A throw from it is logged and doesn't stop later errors being recorded. The first
     * error raised while it runs, thrown or tracked, is then recorded as [SdkErrorType.ErrorSinkFail], subject to the limit.
     * If [reporting] is set, [action] is recording that [SdkErrorType.ErrorSinkFail] and anything raised is dropped; a throw
     * is still logged. Nothing runs once the handler is closed.
     */
    private fun useSink(
        step: String,
        msg: () -> String,
        reporting: SdkErrorSnapshot? = null,
        action: (SdkErrorSink) -> Unit,
    ) {
        recordsInFlight.incrementAndGet()
        try {
            beforeStep(step)
            inSink.set(true)
            // Read after counting this call, so a close or replacing drain either waits for it or is seen here.
            val sink = currentSink() ?: return
            if (reporting != null) {
                holdSinkFailure(reporting)
                runSafely(msg, logcat) {
                    action(sink)
                }
                return
            }
            runSafely(msg, logcat) {
                try {
                    action(sink)
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
            val target = currentSink() ?: return
            runSafely({ "Failed to record SDK error (${SdkErrorType.ErrorSinkFail})" }, logcat) {
                if (limiter.tryAcquire(failure.key())) {
                    target.record(failure)
                }
            }
        } finally {
            inSink.remove()
            sinkFailure.remove()
            recordsInFlight.decrementAndGet()
        }
    }

    private fun currentSink(): SdkErrorSink? = (handlerState.get() as? HandlerState.Recording)?.recorder

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
        class Recording(val recorder: SdkErrorSink) : HandlerState()

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
