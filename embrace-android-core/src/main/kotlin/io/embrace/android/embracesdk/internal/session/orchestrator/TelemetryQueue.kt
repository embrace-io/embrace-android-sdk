package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.session.orchestrator.TelemetryQueue.Companion.COMPACT_THRESHOLD
import java.util.Collections
import java.util.Deque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Buffers telemetry in order before it is written to disk.
 *
 * Telemetry is added and compacted to remove duplicates if [COMPACT_THRESHOLD] is exceeded.
 * [drain] removes from the queue after performing a [compact].
 *
 * [identityOf] supplies the key a record is deduplicated by (the span ID).
 */
internal class TelemetryQueue<T>(
    private val compactThreshold: Int = COMPACT_THRESHOLD,
    private val identityOf: (T) -> Any?,
) {
    private val queue: Deque<T> = ConcurrentLinkedDeque()
    private val pendingRemovals: MutableSet<Any> = Collections.newSetFromMap(ConcurrentHashMap())
    private val buffered = AtomicInteger()
    private val drainLock = Any()

    val size: Int
        get() = buffered.get()

    /**
     * Buffers [item], compacting the queue if it grows past [compactThreshold].
     */
    fun add(item: T) {
        val id = identityOf(item) ?: return
        pendingRemovals.remove(id)
        queue.add(item)

        if (buffered.addAndGet(1) >= compactThreshold) {
            compact()
        }
    }

    /**
     * Buffers [items], compacting the queue if it grows past [compactThreshold].
     */
    fun add(items: List<T>) {
        if (items.isEmpty()) {
            return
        }
        items.forEach(::add)
    }

    /**
     * Removes any instances of [item] that are in the queue.
     */
    fun remove(item: T) {
        val key = identityOf(item) ?: return
        pendingRemovals.add(key)
    }

    /**
     * Returns every telemetry object that should be written after removing them from the queue.
     * Compaction occurs to remove duplicates before this returns.
     */
    fun drain(): List<T> = synchronized(drainLock) {
        compact()
        val drained = mutableListOf<T>()
        while (true) {
            val item = queue.poll() ?: break
            buffered.decrementAndGet()
            drained.add(item)
        }
        drained
    }

    /**
     * Drops every record that a later one supersedes, keeping the last record buffered for each
     * key. [identityOf] provides the identity.
     */
    private fun compact(): Unit = synchronized(drainLock) {
        val superseding = HashSet<Any>()
        val items = queue.descendingIterator()

        while (items.hasNext()) {
            val ref = items.next()
            val key = identityOf(ref) ?: continue
            val id = identityOf(ref)

            if (!superseding.add(key) || pendingRemovals.contains(id)) {
                items.remove()
                pendingRemovals.remove(id)
                buffered.decrementAndGet()
            }
        }
    }

    private companion object {
        private const val COMPACT_THRESHOLD = 1024
    }
}
