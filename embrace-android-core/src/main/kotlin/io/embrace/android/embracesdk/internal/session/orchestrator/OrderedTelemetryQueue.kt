package io.embrace.android.embracesdk.internal.session.orchestrator

import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [TelemetryQueue] that returns every record it buffers, in the order it was added. It is for
 * records that are never superseded, so nothing is ever compacted or removed.
 */
internal class OrderedTelemetryQueue<T> : TelemetryQueue<T> {
    private val queue: Deque<T> = ConcurrentLinkedDeque()
    private val buffered = AtomicInteger()
    private val drainLock = Any()

    override val size: Int
        get() = buffered.get()

    override fun add(item: T) {
        queue.add(item)
        buffered.incrementAndGet()
    }

    override fun add(items: List<T>) {
        if (items.isEmpty()) {
            return
        }
        items.forEach(::add)
    }

    /**
     * Does nothing, as records in this queue are never superseded.
     */
    override fun remove(item: T) = Unit

    override fun drain(): List<T> = synchronized(drainLock) {
        val drained = mutableListOf<T>()
        while (true) {
            val item = queue.poll() ?: break
            buffered.decrementAndGet()
            drained.add(item)
        }
        drained
    }
}
