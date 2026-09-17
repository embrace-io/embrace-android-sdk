package io.embrace.android.embracesdk.internal.session.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanQueueTest {

    @Test
    fun `a drained queue returns what was buffered, in order`() {
        val queue = spanQueue()
        queue.add(listOf(record("a"), record("b")))
        queue.add(listOf(record("c")))
        assertEquals(listOf(record("a"), record("b"), record("c")), queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `draining an empty queue returns nothing`() {
        val queue = spanQueue()
        assertEquals(emptyList<Record>(), queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `an empty batch is ignored`() {
        val queue = spanQueue()
        queue.add(emptyList())
        assertEquals(0, queue.size)
        assertEquals(emptyList<Record>(), queue.drain())
    }

    @Test
    fun `a drain compacts what it returns`() {
        val queue = spanQueue()
        queue.add(listOf(record("a", 1), record("a", 2)))
        assertEquals(listOf(record("a", 2)), queue.drain())
    }

    @Test
    fun `nothing is compacted below the threshold until a drain`() {
        val queue = spanQueue()
        val records = listOf(record("a", 1), record("a", 2), record("a", 3))
        queue.add(records)
        assertEquals(3, queue.size)
        assertEquals(listOf(record("a", 3)), queue.drain())
    }

    @Test
    fun `crossing the threshold compacts the queue`() {
        val queue = spanQueue()
        queue.add(listOf(record("a", 1), record("b", 1), record("a", 2), record("b", 2)))
        assertEquals(2, queue.size)
        assertEquals(listOf(record("a", 2), record("b", 2)), queue.drain())
    }

    @Test
    fun `records buffered before the threshold is crossed are compacted too`() {
        val queue = spanQueue()
        queue.add(listOf(record("a", 1), record("a", 2)))
        assertEquals(2, queue.size)

        queue.add(listOf(record("b", 1), record("a", 3)))
        assertEquals(2, queue.size)
        assertEquals(listOf(record("b", 1), record("a", 3)), queue.drain())
    }

    @Test
    fun `compaction keeps the order the surviving records were added in`() {
        val queue = spanQueue()
        queue.add(listOf(record("a", 1), record("b", 1), record("c", 1), record("b", 2)))
        assertEquals(listOf(record("a", 1), record("c", 1), record("b", 2)), queue.drain())
    }

    @Test
    fun `records with unique keys are left alone when the threshold is crossed`() {
        val queue = spanQueue()
        val records = listOf(record("a"), record("b"), record("c"), record("d"))
        queue.add(records)

        assertEquals(4, queue.size)
        assertEquals(records, queue.drain())
    }

    @Test
    fun `a record with no key is never dropped`() {
        val queue = spanQueue()
        queue.add(listOf(record("a", 1), record(null, 1), record(null, 2), record("a", 2)))
        assertEquals(listOf(record(null, 1), record(null, 2), record("a", 2)), queue.drain())
    }

    @Test
    fun `a drain with nothing to drop returns every record buffered`() {
        val queue = spanQueue()
        val records = listOf(record("a"), record("b"))
        queue.add(records)

        assertEquals(records, queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `the buffered size tracks what compaction drops and what a drain takes`() {
        val queue = spanQueue()
        queue.add(listOf(record("a", 1), record("b", 1), record("c", 1)))
        assertEquals(3, queue.size)

        queue.add(listOf(record("a", 2)))
        assertEquals(3, queue.size)

        assertEquals(3, queue.drain().size)
        assertEquals(0, queue.size)
    }

    private fun spanQueue() = SpanQueue<Record>(compactThreshold = THRESHOLD, identityOf = Record::key)

    private fun record(key: String?, version: Int = 0) = Record(key, version)

    private data class Record(val key: String?, val version: Int)

    private companion object {
        private const val THRESHOLD = 4
    }
}
