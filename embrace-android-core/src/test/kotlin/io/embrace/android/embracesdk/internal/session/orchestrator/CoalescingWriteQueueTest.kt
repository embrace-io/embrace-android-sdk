package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class CoalescingWriteQueueTest {

    private companion object {
        private const val DELAY_MS = 5000L
    }

    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var queue: CoalescingWriteQueue

    private val writes = mutableListOf<String>()

    private fun write(name: String) = Runnable { writes.add(name) }

    @Before
    fun setUp() {
        executor = BlockingScheduledExecutorService(FakeClock(), blockingMode = false)
        queue = CoalescingWriteQueue(BackgroundWorker(executor), DELAY_MS)
        writes.clear()
    }

    @Test
    fun `a write waits out its delay`() {
        queue.submit(write("first"))

        assertEquals(1, executor.scheduledTasksCount())
        assertEquals(emptyList<String>(), writes)

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf("first"), writes)
    }

    @Test
    fun `writes submitted within the delay collapse to the last one`() {
        queue.submit(write("first"))
        executor.moveForwardAndRunBlocked(DELAY_MS - 1)
        queue.submit(write("second"))
        queue.submit(write("third"))

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf("third"), writes)
    }

    @Test
    fun `writes submitted after the delay all run`() {
        queue.submit(write("first"))
        executor.moveForwardAndRunBlocked(DELAY_MS)
        queue.submit(write("second"))
        executor.moveForwardAndRunBlocked(DELAY_MS)

        assertEquals(listOf("first", "second"), writes)
    }

    @Test
    fun `flush runs the pending write immediately`() {
        queue.submit(write("first"))
        queue.flush()
        assertEquals(listOf("first"), writes)
    }

    @Test
    fun `a write forced by flush does not run again when its delay elapses`() {
        queue.submit(write("first"))
        queue.flush()

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf("first"), writes)
        assertEquals(0, executor.scheduledTasksCount())
    }

    @Test
    fun `flush with nothing pending does nothing`() {
        queue.flush()
        assertEquals(emptyList<String>(), writes)
    }

    @Test
    fun `flush twice runs the pending write once`() {
        queue.submit(write("first"))
        queue.flush()
        queue.flush()
        assertEquals(listOf("first"), writes)
    }

    @Test
    fun `a write submitted after flush is armed as normal`() {
        queue.submit(write("first"))
        queue.flush()

        queue.submit(write("second"))
        executor.moveForwardAndRunBlocked(DELAY_MS)

        assertEquals(listOf("first", "second"), writes)
    }

    @Test
    fun `a write that throws does not fail flush`() {
        queue.submit { error("write failed") }
        queue.flush()
        assertEquals(emptyList<String>(), writes)
    }

    @Test
    fun `flush drops a write that the worker will not take`() {
        executor.shutdown()
        queue.submit(write("first"))
        queue.flush()
        assertEquals(emptyList<String>(), writes)
    }

    @Test
    fun `flush does not wait for the write to run`() {
        val blockedExecutor = BlockingScheduledExecutorService(FakeClock(), blockingMode = true)
        val blockedQueue = CoalescingWriteQueue(BackgroundWorker(blockedExecutor), DELAY_MS)

        blockedQueue.submit(write("first"))
        blockedQueue.flush()
        assertEquals(emptyList<String>(), writes)

        blockedExecutor.runCurrentlyBlocked()
        assertEquals(listOf("first"), writes)
    }

    @Test
    fun `a write superseded while it is being armed does not displace the newer write`() {
        var queueRef: CoalescingWriteQueue? = null
        val hookedExecutor = ScheduleHookExecutor(executor) {
            checkNotNull(queueRef).submit(write("second"))
        }
        val reentrantQueue = CoalescingWriteQueue(BackgroundWorker(hookedExecutor), DELAY_MS)
        queueRef = reentrantQueue

        reentrantQueue.submit(write("first"))
        executor.moveForwardAndRunBlocked(DELAY_MS)

        assertEquals(listOf("second"), writes)
    }

    /**
     * Delegates to [delegate], calling [onFirstSchedule] once after the first write is scheduled
     * but before the caller has published it.
     */
    private class ScheduleHookExecutor(
        private val delegate: ScheduledExecutorService,
        private val onFirstSchedule: () -> Unit,
    ) : ScheduledExecutorService by delegate {

        private var hooked = false

        override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
            val future = delegate.schedule(command, delay, unit)
            if (!hooked) {
                hooked = true
                onFirstSchedule()
            }
            return future
        }
    }
}
