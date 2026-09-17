package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Batches the items appended to one file in a session part, so that a burst of appends costs one
 * write rather than one each. Nothing is dropped: a batch that is superseded is merged into the one
 * that supersedes it, and the [DebouncedTrigger] it is armed with bounds how long it waits.
 *
 * [compact] reduces a batch whose records supersede each other, such as a span that changed twice.
 * It runs before every write, and on the buffer itself once that holds more than [compactThreshold]
 * items. A file whose records are all distinct passes none, and is never reordered.
 *
 * [write] is handed each batch on the [worker], and this class takes no lock.
 */
internal class BatchingWriteQueue<T>(
    private val worker: BackgroundWorker,
    delayMs: Long,
    private val compact: ((List<T>) -> List<T>)? = null,
    private val compactThreshold: Int = DEFAULT_COMPACT_THRESHOLD,
    private val write: (List<T>) -> Unit,
) {

    /** The items waiting to be written, in the order they were submitted. */
    private val buffer = ConcurrentLinkedQueue<T>()

    /** What [buffer] holds, counted separately as its own size is not a constant time read. */
    private val buffered = AtomicInteger()

    private val trigger = DebouncedTrigger(worker, delayMs, Runnable(::drain))

    /**
     * Buffers [items] for the next write, arming one if nothing is armed already. Returns whether
     * the queue reached the [worker], so that a caller whose items are only buffered can hold on to
     * its own copy of them. They are written either way by a later submission that does reach it.
     */
    fun submit(items: List<T>): Boolean {
        if (items.isEmpty()) {
            return true
        }
        buffer.addAll(items)
        if (buffered.addAndGet(items.size) > compactThreshold) {
            compactBuffer()
        }
        return trigger.arm()
    }

    /**
     * Writes everything buffered on the worker now, disarming the trigger so the batch is not
     * written twice. This call does not block, and submits straight to the [worker] so that it
     * still runs once the process is terminating.
     */
    fun flush() {
        // disarmed before the batch is taken, so an item arriving in between arms a write of its
        // own rather than being stranded in the buffer
        trigger.disarm()
        val batch = takeBatch().ifEmpty { return }
        runCatching { worker.submit { write(batch) } }
    }

    private fun drain() {
        val batch = takeBatch()
        if (batch.isNotEmpty()) {
            write(batch)
        }
    }

    /**
     * Empties the buffer into the batch to write. Two drains at once take disjoint halves of it, so
     * nothing is written twice and nothing is left behind.
     */
    private fun takeBatch(): List<T> = compacted(drainBuffer())

    /**
     * Drops the duplicates buffered so far. A drain running at the same time takes what it polled
     * to disk, so at worst an item is written here and there, which is what superseding it costs.
     */
    private fun compactBuffer() {
        if (compact == null) {
            return
        }
        val compacted = compacted(drainBuffer())
        buffer.addAll(compacted)
        buffered.addAndGet(compacted.size)
    }

    private fun drainBuffer(): List<T> {
        val drained = generateSequence { buffer.poll() }.toList()
        buffered.addAndGet(-drained.size)
        return drained
    }

    private fun compacted(items: List<T>): List<T> = compact?.invoke(items) ?: items

    private companion object {

        /** What a buffer holds before its duplicates are dropped. */
        const val DEFAULT_COMPACT_THRESHOLD: Int = 1024
    }
}
