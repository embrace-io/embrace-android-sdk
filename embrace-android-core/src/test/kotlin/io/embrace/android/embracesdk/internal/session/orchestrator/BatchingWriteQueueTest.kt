package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class BatchingWriteQueueTest {

    private companion object {
        private const val DELAY_MS = 5000L
        private const val NO_COMPACTION = 1000
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10L
    }

    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var queue: BatchingWriteQueue<String>

    private val batches = mutableListOf<List<String>>()

    @Before
    fun setUp() {
        executor = BlockingScheduledExecutorService(FakeClock(), blockingMode = false)
        batches.clear()
        queue = createQueue(BackgroundWorker(executor))
    }

    /** Keeps the last item submitted for each key, as the span snapshot queue does by span ID. */
    private fun lastPerKey(items: List<String>): List<String> =
        items.associateBy { it.first() }.values.toList()

    private fun createQueue(
        worker: BackgroundWorker,
        compact: ((List<String>) -> List<String>)? = null,
        compactThreshold: Int = NO_COMPACTION,
    ) = BatchingWriteQueue(worker, DELAY_MS, compact, compactThreshold) { batches.add(it) }

    @Test
    fun `items submitted within the delay are written as one batch, in order`() {
        queue.submit(listOf("first", "second"))
        queue.submit(listOf("third"))
        assertEquals(1, executor.scheduledTasksCount())
        assertEquals(emptyList<List<String>>(), batches)

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("first", "second", "third")), batches)

        // the batch is closed once written, so the next item arms one of its own
        queue.submit(listOf("fourth"))
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("first", "second", "third"), listOf("fourth")), batches)
    }

    @Test
    fun `nothing reaches the worker when there is nothing to write`() {
        assertTrue(queue.submit(emptyList()))
        queue.flush()

        assertEquals(emptyList<List<String>>(), batches)
        assertEquals(0, executor.scheduledTasksCount())
        assertEquals(0, executor.submitCount)
    }

    @Test
    fun `flush writes the buffered batch once, and the next item arms a batch of its own`() {
        queue.submit(listOf("first"))
        queue.flush()
        queue.flush()
        assertEquals(listOf(listOf("first")), batches)

        // the disarmed trigger does not write the batch again when its delay elapses
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("first")), batches)
        assertEquals(0, executor.scheduledTasksCount())

        queue.submit(listOf("second"))
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("first"), listOf("second")), batches)
    }

    @Test
    fun `flush does not wait for the write to run`() {
        val blockedExecutor = BlockingScheduledExecutorService(FakeClock(), blockingMode = true)
        val blocked = createQueue(BackgroundWorker(blockedExecutor))

        blocked.submit(listOf("first"))
        blocked.flush()
        assertEquals(emptyList<List<String>>(), batches)

        blockedExecutor.runCurrentlyBlocked()
        assertEquals(listOf(listOf("first")), batches)
    }

    @Test
    fun `items buffered by a submission that could not be armed are written by the next one`() {
        val recovering = createQueue(BackgroundWorker(FailingScheduleExecutor(executor)))

        assertFalse(recovering.submit(listOf("first")))
        assertTrue(recovering.submit(listOf("second")))

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("first", "second")), batches)
    }

    @Test
    fun `an item submitted while a batch is being armed survives a flush racing that arming`() {
        var queueRef: BatchingWriteQueue<String>? = null
        val hooked = ScheduleHookExecutor(executor) {
            val target = checkNotNull(queueRef)
            target.flush()
            target.submit(listOf("second"))
        }
        val reentrant = createQueue(BackgroundWorker(hooked))
        queueRef = reentrant

        reentrant.submit(listOf("first"))
        executor.moveForwardAndRunBlocked(DELAY_MS)

        // nothing is stranded: the flush took the first item and the second armed a batch of its own
        assertEquals(listOf("first", "second"), batches.flatten())
    }

    @Test
    fun `every item is written exactly once when submissions and flushes contend`() {
        val realExecutor = Executors.newSingleThreadScheduledExecutor()
        val written = Collections.synchronizedList(mutableListOf<String>())
        val contended = BatchingWriteQueue<String>(BackgroundWorker(realExecutor), delayMs = 1) {
            written.addAll(it)
        }

        val threads = List(4) { thread ->
            Thread {
                repeat(250) { item ->
                    contended.submit(listOf("$thread-$item"))
                    if (item % 50 == 0) {
                        contended.flush()
                    }
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        contended.flush()
        realExecutor.shutdown()
        assertTrue(realExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val expected = List(4) { thread -> List(250) { "$thread-$it" } }.flatten()
        assertEquals(expected.size, written.size)
        assertEquals(expected.toSet(), written.toSet())
    }

    @Test
    fun `duplicates are dropped from the batch that is written, keeping the last of them`() {
        val deduping = createQueue(BackgroundWorker(executor), compact = ::lastPerKey)

        deduping.submit(listOf("a1", "b1", "a2"))
        deduping.submit(listOf("b2"))
        executor.moveForwardAndRunBlocked(DELAY_MS)

        assertEquals(listOf(listOf("a2", "b2")), batches)
    }

    @Test
    fun `duplicates are dropped from the buffer once it holds more than the threshold`() {
        val compacted = mutableListOf<Int>()
        val deduping = createQueue(
            worker = BackgroundWorker(executor),
            compact = { items ->
                compacted.add(items.size)
                lastPerKey(items)
            },
            compactThreshold = 3,
        )

        repeat(8) { deduping.submit(listOf("first")) }

        // compacted back to one item whenever a fourth joins the buffer, rather than growing
        assertEquals(listOf(4, 4), compacted)
        deduping.flush()
        assertEquals(listOf(listOf("first")), batches)
    }

    @Test
    fun `a queue with no compaction keeps every item, in order, past the threshold`() {
        val plain = createQueue(BackgroundWorker(executor), compactThreshold = 2)

        repeat(5) { plain.submit(listOf("item-$it")) }
        executor.moveForwardAndRunBlocked(DELAY_MS)

        assertEquals(listOf(List(5) { "item-$it" }), batches)
    }

    @Test
    fun `a write that throws does not break the queue`() {
        val throwing = BatchingWriteQueue<String>(BackgroundWorker(executor), DELAY_MS) {
            error("write failed")
        }

        throwing.submit(listOf("first"))
        runCatching { executor.moveForwardAndRunBlocked(DELAY_MS) }

        assertTrue(throwing.submit(listOf("second")))
    }
}
