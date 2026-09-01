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
        private const val TIMEOUT_MS = 1000L
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
    fun `shutdown runs the pending write immediately`() {
        queue.submit(write("first"))
        queue.shutdown(TIMEOUT_MS)
        assertEquals(listOf("first"), writes)
    }

    @Test
    fun `a write forced by shutdown does not run again when its delay elapses`() {
        queue.submit(write("first"))
        queue.shutdown(TIMEOUT_MS)

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf("first"), writes)
        assertEquals(0, executor.scheduledTasksCount())
    }

    @Test
    fun `shutdown with nothing pending does nothing`() {
        queue.shutdown(TIMEOUT_MS)
        assertEquals(emptyList<String>(), writes)
    }

    @Test
    fun `shutdown twice runs the pending write once`() {
        queue.submit(write("first"))
        queue.shutdown(TIMEOUT_MS)
        queue.shutdown(TIMEOUT_MS)
        assertEquals(listOf("first"), writes)
    }

    @Test
    fun `a write submitted after shutdown is armed as normal`() {
        queue.submit(write("first"))
        queue.shutdown(TIMEOUT_MS)

        queue.submit(write("second"))
        executor.moveForwardAndRunBlocked(DELAY_MS)

        assertEquals(listOf("first", "second"), writes)
    }

    @Test
    fun `a write that throws does not fail shutdown`() {
        queue.submit { error("write failed") }
        queue.shutdown(TIMEOUT_MS)
        assertEquals(emptyList<String>(), writes)
    }

    @Test
    fun `a write submitted to a shut down worker is flushed by shutdown`() {
        executor.shutdown()
        queue.submit(write("first"))

        assertEquals(emptyList<String>(), writes)

        queue.shutdown(TIMEOUT_MS)
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
