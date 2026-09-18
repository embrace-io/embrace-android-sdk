package io.embrace.android.embracesdk.internal.session.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class TelemetryQueueTest {

    @Test
    fun `a drained queue returns what was buffered, in order`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a"), record("b")))
        queue.add(listOf(record("c")))
        assertEquals(listOf(record("a"), record("b"), record("c")), queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `draining an empty queue returns nothing`() {
        val queue = telemetryQueue()
        assertEquals(emptyList<Record>(), queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `an empty batch is ignored`() {
        val queue = telemetryQueue()
        queue.add(emptyList())
        assertEquals(0, queue.size)
        assertEquals(emptyList<Record>(), queue.drain())
    }

    @Test
    fun `a drain compacts what it returns`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record("a", 2)))
        assertEquals(listOf(record("a", 2)), queue.drain())
    }

    @Test
    fun `nothing is compacted below the threshold until a drain`() {
        val queue = telemetryQueue()
        val records = listOf(record("a", 1), record("a", 2), record("a", 3))
        queue.add(records)
        assertEquals(3, queue.size)
        assertEquals(listOf(record("a", 3)), queue.drain())
    }

    @Test
    fun `crossing the threshold compacts the queue`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record("b", 1), record("a", 2), record("b", 2)))
        assertEquals(2, queue.size)
        assertEquals(listOf(record("a", 2), record("b", 2)), queue.drain())
    }

    @Test
    fun `records buffered before the threshold is crossed are compacted too`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record("a", 2)))
        assertEquals(2, queue.size)

        queue.add(listOf(record("b", 1), record("a", 3)))
        assertEquals(2, queue.size)
        assertEquals(listOf(record("b", 1), record("a", 3)), queue.drain())
    }

    @Test
    fun `compaction keeps the order the surviving records were added in`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record("b", 1), record("c", 1), record("b", 2)))
        assertEquals(listOf(record("a", 1), record("c", 1), record("b", 2)), queue.drain())
    }

    @Test
    fun `records with unique keys are left alone when the threshold is crossed`() {
        val queue = telemetryQueue()
        val records = listOf(record("a"), record("b"), record("c"), record("d"))
        queue.add(records)

        assertEquals(4, queue.size)
        assertEquals(records, queue.drain())
    }

    @Test
    fun `a record with no key is never dropped`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record(null, 1), record(null, 2), record("a", 2)))
        assertEquals(listOf(record(null, 1), record(null, 2), record("a", 2)), queue.drain())
    }

    @Test
    fun `a drain with nothing to drop returns every record buffered`() {
        val queue = telemetryQueue()
        val records = listOf(record("a"), record("b"))
        queue.add(records)

        assertEquals(records, queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `the buffered size tracks what compaction drops and what a drain takes`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record("b", 1), record("c", 1)))
        assertEquals(3, queue.size)

        queue.add(listOf(record("a", 2)))
        assertEquals(3, queue.size)

        assertEquals(3, queue.drain().size)
        assertEquals(0, queue.size)
    }

    @Test
    fun `records added one at a time are drained in order`() {
        val queue = telemetryQueue()
        queue.add(record("a"))
        queue.add(record("b"))
        queue.add(record("c"))

        assertEquals(3, queue.size)
        assertEquals(listOf(record("a"), record("b"), record("c")), queue.drain())
        assertEquals(0, queue.size)
    }

    @Test
    fun `a single record added below the threshold is not compacted until a drain`() {
        val queue = telemetryQueue()
        queue.add(record("a", 1))
        queue.add(record("a", 2))
        assertEquals(2, queue.size)
        assertEquals(listOf(record("a", 2)), queue.drain())
    }

    @Test
    fun `a single record that crosses the threshold compacts the queue`() {
        val queue = telemetryQueue()
        queue.add(record("a", 1))
        queue.add(record("b", 1))
        queue.add(record("a", 2))
        assertEquals(3, queue.size)

        queue.add(record("b", 2))
        assertEquals(2, queue.size)
        assertEquals(listOf(record("a", 2), record("b", 2)), queue.drain())
    }

    @Test
    fun `a single record with no key is never dropped`() {
        val queue = telemetryQueue()
        queue.add(record(null, 1))
        queue.add(record(null, 2))
        queue.add(record("a", 1))
        queue.add(record("a", 2))

        assertEquals(3, queue.size)
        assertEquals(listOf(record(null, 1), record(null, 2), record("a", 2)), queue.drain())
    }

    @Test
    fun `single and batch adds share the same buffer`() {
        val queue = telemetryQueue()
        queue.add(record("a", 1))
        queue.add(listOf(record("b", 1), record("c", 1)))

        assertEquals(3, queue.size)
        assertEquals(listOf(record("a", 1), record("b", 1), record("c", 1)), queue.drain())
    }

    @Test
    fun `a single record crossing the threshold compacts records added in a batch`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record("b", 1), record("a", 2)))
        assertEquals(3, queue.size)

        queue.add(record("c", 1))
        assertEquals(3, queue.size)
        assertEquals(listOf(record("b", 1), record("a", 2), record("c", 1)), queue.drain())
    }

    @Test
    fun `a batch can supersede a record added on its own`() {
        val queue = telemetryQueue()
        queue.add(record("a", 1))
        queue.add(listOf(record("a", 2)))
        assertEquals(listOf(record("a", 2)), queue.drain())
    }

    @Test
    fun `a queue can be reused after it is drained`() {
        val queue = telemetryQueue()
        queue.add(record("a", 1))
        assertEquals(listOf(record("a", 1)), queue.drain())

        queue.add(record("a", 2))
        assertEquals(1, queue.size)
        assertEquals(listOf(record("a", 2)), queue.drain())
    }

    @Test
    fun `removing a record drops it from the next drain`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record("b", 1)))

        queue.remove(record("a", 2))
        assertEquals(1, queue.size)
        assertEquals(listOf(record("b", 1)), queue.drain())
    }

    @Test
    fun `removing a record drops every copy of it that is buffered`() {
        val queue = telemetryQueue()
        queue.add(listOf(record("a", 1), record("b", 1), record("a", 2)))

        queue.remove(record("a", 3))
        assertEquals(1, queue.size)
        assertEquals(listOf(record("b", 1)), queue.drain())
    }

    @Test
    fun `removing a record that was never buffered does nothing`() {
        val queue = telemetryQueue()
        queue.add(record("a", 1))

        queue.remove(record("b", 1))
        assertEquals(1, queue.size)
        assertEquals(listOf(record("a", 1)), queue.drain())
    }

    @Test
    fun `a record with no key removes nothing`() {
        val queue = telemetryQueue()
        queue.add(listOf(record(null, 1), record("a", 1)))

        queue.remove(record(null, 2))
        assertEquals(2, queue.size)
        assertEquals(listOf(record(null, 1), record("a", 1)), queue.drain())
    }

    @Test
    fun `a removed record can be buffered again`() {
        val queue = telemetryQueue()
        queue.add(record("a", 1))
        queue.remove(record("a", 1))
        assertEquals(0, queue.size)

        queue.add(record("a", 2))
        assertEquals(listOf(record("a", 2)), queue.drain())
    }

    @Test
    fun `records added from several threads are all drained exactly once`() {
        val queue = telemetryQueue()
        val threads = 4
        val perThread = 250
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)

        try {
            repeat(threads) { thread ->
                executor.submit {
                    start.await()
                    repeat(perThread) { index -> queue.add(record("$thread-$index")) }
                }
            }
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(TIMEOUT_SECS, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
        val drained = queue.drain()
        assertEquals(threads * perThread, drained.size)
        assertEquals(drained.size, drained.toSet().size)
        assertEquals(0, queue.size)
    }

    private fun telemetryQueue() = TelemetryQueue<Record>(compactThreshold = THRESHOLD, identityOf = Record::key)

    private fun record(key: String?, version: Int = 0) = Record(key, version)

    private data class Record(val key: String?, val version: Int)

    private companion object {
        private const val THRESHOLD = 4
        private const val TIMEOUT_SECS = 10L
    }
}
