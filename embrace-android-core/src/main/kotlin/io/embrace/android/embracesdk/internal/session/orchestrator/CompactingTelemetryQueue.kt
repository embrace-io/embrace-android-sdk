package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.session.orchestrator.CompactingTelemetryQueue.Companion.COMPACT_THRESHOLD
import java.util.Collections
import java.util.Deque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [TelemetryQueue] that drops elements a later element with the same identity supersedes.
 *
 * Telemetry is added and compacted to remove duplicates if [COMPACT_THRESHOLD] is exceeded.
 * [drain] removes from the queue after performing a [compact].
 *
 * [identityOf] supplies the key a record is deduplicated by.
 */
internal class CompactingTelemetryQueue<T>(
    private val compactThreshold: Int = COMPACT_THRESHOLD,
    private val identityOf: (T) -> Any?,
) : TelemetryQueue<T> {
    private val queue: Deque<T> = ConcurrentLinkedDeque()
    private val pendingRemovals: MutableSet<Any> = Collections.newSetFromMap(ConcurrentHashMap())
    private val buffered = AtomicInteger()
    private val drainLock = Any()

    override val size: Int
        get() = buffered.get()

    /**
     * Buffers [item], compacting the queue if it grows past [compactThreshold].
     */
    override fun add(item: T) {
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
    override fun add(items: List<T>) {
        if (items.isEmpty()) {
            return
        }
        items.forEach(::add)
    }

    override fun remove(item: T) {
        val key = identityOf(item) ?: return
        pendingRemovals.add(key)
    }

    /**
     * Returns every telemetry object that should be written after removing them from the queue.
     * Compaction occurs to remove duplicates before this returns.
     */
    override fun drain(): List<T> = synchronized(drainLock) {
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
        val removals = pendingRemovals.toHashSet()
        val superseding = HashSet<Any>()
        val items = queue.descendingIterator()

        while (items.hasNext()) {
            val key = identityOf(items.next()) ?: continue

            if (!superseding.add(key) || key in removals) {
                items.remove()
                buffered.decrementAndGet()
            }
        }
        pendingRemovals.removeAll(removals)
    }

    private companion object {
        private const val COMPACT_THRESHOLD = 1024
    }
}
