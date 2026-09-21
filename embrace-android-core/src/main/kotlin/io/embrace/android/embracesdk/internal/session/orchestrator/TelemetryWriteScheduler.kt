package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Controls the logic for when buffered telemetry should be written. Telemetry is added via [write]
 * and then buffered internally in [TelemetryQueue].
 *
 * The chosen [WriteStrategy] dictates when the queue is drained. [WriteStrategy.DEBOUNCED] will wait
 * for an appropriate time threshold to prevent bursty operations causing lots of sequential disk writes.
 * [WriteStrategy.IMMEDIATE] will attempt to write immediately on a background worker, cancelling any scheduled
 * tasks that are not already in-progress.
 *
 * Note that telemetry is always written on the worker - the caller is responsible for waiting for writes to finish
 * in scenarios such as a crash.
 */
internal class TelemetryWriteScheduler<T>(
    private val worker: BackgroundWorker,
    private val delayMs: Long,
    private val queue: TelemetryQueue<T>,
    private val guard: (Runnable) -> Runnable,
    private val onWrite: (List<T>) -> Unit,
) {
    internal enum class WriteStrategy {

        /**
         * Debounce writes so that they happen after a delay has elapsed. This should be the default case.
         */
        DEBOUNCED,

        /**
         * Enqueue a write on the worker immediately. This should be for moments where the SDK can't wait (i.e. transitions or crash time).
         */
        IMMEDIATE,
    }

    private val armed = AtomicBoolean()
    private val trigger = AtomicReference<Future<*>?>(null)

    /**
     * Buffers [item] and arms a write using the supplied [WriteStrategy].
     */
    fun write(timing: WriteStrategy, item: T) {
        queue.add(item)
        applyTiming(timing)
    }

    /**
     * Buffers [items] and arms a write using the supplied [WriteStrategy].
     */
    fun write(
        timing: WriteStrategy,
        items: List<T>,
    ) {
        queue.add(items)
        applyTiming(timing)
    }

    /**
     * Removes [item] from the queue.
     */
    fun remove(item: T) = queue.remove(item)

    private fun applyTiming(timing: WriteStrategy) {
        when (timing) {
            WriteStrategy.DEBOUNCED -> arm()
            WriteStrategy.IMMEDIATE -> flush()
        }
    }

    /**
     * Arms a write [delayMs] from now. If the scheduler is always armed this has no effect as the existing write will drain the queue.
     */
    private fun arm() {
        if (!armed.compareAndSet(false, true)) {
            return
        }
        val write = writeTask()
        val task = Runnable {
            armed.set(false)
            write.run()
        }
        val scheduled = runCatching {
            worker.schedule<Unit>(task, delayMs, TimeUnit.MILLISECONDS)
        }.getOrNull()

        if (scheduled == null) {
            armed.set(false)
        } else {
            trigger.set(scheduled)
        }
    }

    /**
     * Disarms any waiting writes and enqueues a new write on the worker with no delay.
     *
     * If a Runnable is already in-progress it is allowed to complete.
     */
    fun flush() {
        disarm()
        if (queue.size == 0) {
            return
        }
        val task = guard(Runnable { onWrite(queue.drain()) })
        runCatching { worker.submit(task) }
        runCatching { worker.submit(writeTask()) }
    }

    private fun writeTask(): Runnable = guard(
        Runnable {
            val drained = queue.drain()
            if (drained.isNotEmpty()) {
                onWrite(drained)
            }
        },
    )

    private fun disarm() {
        trigger.getAndSet(null)?.cancel(false)
        armed.set(false)
    }
}
