package io.embrace.android.embracesdk.internal.session.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Test

internal class OrderedTelemetryQueueTest {

    @Test
    fun `a drained queue returns what was buffered, in order`() {
        val queue = OrderedTelemetryQueue<Record>()
        queue.add(listOf(record("a"), record("b")))
        queue.add(listOf(record("c")))
        assertEquals(listOf(record("a"), record("b"), record("c")), queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `draining an empty queue returns nothing`() {
        val queue = OrderedTelemetryQueue<Record>()
        assertEquals(emptyList<Record>(), queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `an empty batch is ignored`() {
        val queue = OrderedTelemetryQueue<Record>()
        queue.add(emptyList())
        assertEquals(0, queue.size)
        assertEquals(emptyList<Record>(), queue.drain())
    }

    @Test
    fun `records with the same key are all kept`() {
        val queue = OrderedTelemetryQueue<Record>()
        val records = listOf(record("a", 1), record("b", 1), record("a", 2), record("b", 2))
        queue.add(records)
        assertEquals(4, queue.size)
        assertEquals(records, queue.drain())
    }

    @Test
    fun `a large number of records with the same key is never compacted`() {
        val queue = OrderedTelemetryQueue<Record>()
        val records = (0 until LARGE_COUNT).map { record("a", it) }
        queue.add(records)
        assertEquals(LARGE_COUNT, queue.size)
        assertEquals(records, queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `a record with no key is kept`() {
        val queue = OrderedTelemetryQueue<Record>()
        val records = listOf(record(null, 1), record("a", 1), record(null, 2))
        queue.add(records)
        assertEquals(records, queue.drain())
    }

    @Test
    fun `records added one at a time are drained in order`() {
        val queue = OrderedTelemetryQueue<Record>()
        queue.add(record("a"))
        queue.add(record("b"))
        queue.add(record("c"))

        assertEquals(3, queue.size)
        assertEquals(listOf(record("a"), record("b"), record("c")), queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `single and batch adds share the same buffer`() {
        val queue = OrderedTelemetryQueue<Record>()
        queue.add(record("a", 1))
        queue.add(listOf(record("b", 1), record("a", 2)))

        assertEquals(3, queue.size)
        assertEquals(listOf(record("a", 1), record("b", 1), record("a", 2)), queue.drain())
    }

    @Test
    fun `the buffered size tracks adds and drains`() {
        val queue = OrderedTelemetryQueue<Record>()
        queue.add(listOf(record("a"), record("b")))
        assertEquals(2, queue.size)

        queue.add(record("a"))
        assertEquals(3, queue.size)

        assertEquals(3, queue.drain().size)
        assertEquals(0, queue.size)
    }

    @Test
    fun `a queue can be reused after it is drained`() {
        val queue = OrderedTelemetryQueue<Record>()
        queue.add(record("a", 1))
        assertEquals(listOf(record("a", 1)), queue.drain())

        queue.add(record("a", 2))
        assertEquals(1, queue.size)
        assertEquals(listOf(record("a", 2)), queue.drain())
    }

    @Test
    fun `removing a record does nothing`() {
        val queue = OrderedTelemetryQueue<Record>()
        val records = listOf(record("a"), record("b"))
        queue.add(records)

        queue.remove(record("b"))
        assertEquals(2, queue.size)
        assertEquals(records, queue.drain())
    }

    private fun record(key: String?, version: Int = 0) = Record(key, version)

    private data class Record(val key: String?, val version: Int)

    private companion object {
        private const val LARGE_COUNT = 2048
    }
}
