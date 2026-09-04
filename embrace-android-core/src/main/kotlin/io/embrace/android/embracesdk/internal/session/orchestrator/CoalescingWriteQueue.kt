package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Debounces the writes for one file in a session part. Each write persists the whole file, so a
 * write that is superseded before it runs can be dropped. The intent behind this class
 * is that a burst of changes typically only cost one write, rather than one per change.
 *
 * A write is held as a [FutureTask] armed with a scheduled trigger that runs it
 * [delayMs] later. Cancelling that trigger does not cancel the [FutureTask] it wraps. This
 * lets [flush] force the task to run early rather than queueing the work twice.
 */
internal class CoalescingWriteQueue(
    private val worker: BackgroundWorker,
    private val delayMs: Long = DEFAULT_DELAY_MS,
) {
    private val pending = AtomicReference<PendingWrite?>(null)

    /**
     * Orders writes so that concurrent calls to [submit] can be compared by age. This handles interleaving
     * during submission.
     */
    private val sequence = AtomicLong()

    /**
     * Arms [runnable] to run [delayMs] from now, disarming whatever was armed before it.
     * In-progress writes are left to finish.
     */
    fun submit(runnable: Runnable) {
        val seq = sequence.incrementAndGet()
        val task = FutureTask(runnable, Unit)
        val trigger = runCatching {
            worker.schedule<Unit>(task, delayMs, TimeUnit.MILLISECONDS)
        }.getOrNull()
        val write = PendingWrite(seq, task, trigger)

        while (true) {
            val previous = pending.get()
            if (previous != null && previous.seq > seq) {
                // a newer write was armed while this one was being armed. cancel this one.
                trigger?.cancel(false)
                return
            }
            if (pending.compareAndSet(previous, write)) {
                previous?.trigger?.cancel(false)
                return
            }
        }
    }

    /**
     * Forces the pending write onto the worker now, disarming its scheduled trigger so that the
     * write does not run twice. This call does not block.
     */
    fun flush() {
        val write = pending.getAndSet(null) ?: return
        write.trigger?.cancel(false)
        runCatching { worker.submit(write.task) }
    }

    private class PendingWrite(
        val seq: Long,
        val task: FutureTask<Unit>,
        val trigger: Future<*>?,
    )

    companion object {
        const val DEFAULT_DELAY_MS = 0L
    }
}
