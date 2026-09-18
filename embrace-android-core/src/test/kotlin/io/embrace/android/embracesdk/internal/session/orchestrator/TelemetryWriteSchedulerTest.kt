package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.session.orchestrator.TelemetryWriteScheduler.WriteStrategy
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class TelemetryWriteSchedulerTest {

    private companion object {
        private const val DELAY_MS = 5000L
    }

    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var scheduler: TelemetryWriteScheduler<String>

    private val writes = mutableListOf<List<String>>()
    private var guardedTasks = 0
    private var writesAllowed = true
    private var onWrite: (List<String>) -> Unit = {}

    @Before
    fun setUp() {
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, blockingMode = false)
        writes.clear()
        guardedTasks = 0
        writesAllowed = true
        onWrite = {}
        scheduler = createScheduler()
    }

    @Test
    fun `a debounced write waits out its delay`() {
        scheduler.write(WriteStrategy.DEBOUNCED, "a")
        assertEquals(emptyList<List<String>>(), writes)
        assertEquals(1, executor.scheduledTasksCount())

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a")), writes)
    }

    @Test
    fun `a burst of telemetry costs one write`() {
        scheduler.write(WriteStrategy.DEBOUNCED, "a")
        clock.tick(1000)
        scheduler.write(WriteStrategy.DEBOUNCED, listOf("b", "c"))
        clock.tick(1000)
        scheduler.write(WriteStrategy.DEBOUNCED, "d")

        assertEquals(1, executor.scheduledTasksCount())
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a", "b", "c", "d")), writes)
    }

    @Test
    fun `a record written on its own reaches the worker`() {
        scheduler.write(WriteStrategy.IMMEDIATE, "a")
        assertEquals(listOf(listOf("a")), writes)
        assertEquals(0, executor.scheduledTasksCount())
    }

    @Test
    fun `an immediate write disarms the write that was waiting`() {
        scheduler.write(WriteStrategy.DEBOUNCED, "a")
        scheduler.write(WriteStrategy.IMMEDIATE, emptyList())
        assertEquals(listOf(listOf("a")), writes)

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a")), writes)
    }

    @Test
    fun `an immediate write runs even when nothing is buffered`() {
        scheduler.write(WriteStrategy.IMMEDIATE, emptyList())
        assertEquals(listOf(emptyList<String>()), writes)
    }

    @Test
    fun `a flush writes what is buffered without waiting`() {
        scheduler.write(WriteStrategy.DEBOUNCED, "a")
        scheduler.flush()
        assertEquals(listOf(listOf("a")), writes)
        assertEquals(0, executor.scheduledTasksCount())

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a")), writes)
    }

    @Test
    fun `a write is armed again once the one before it has run`() {
        scheduler.write(WriteStrategy.DEBOUNCED, "a")
        executor.moveForwardAndRunBlocked(DELAY_MS)

        scheduler.write(WriteStrategy.DEBOUNCED, "b")
        assertEquals(1, executor.scheduledTasksCount())

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a"), listOf("b")), writes)
    }

    @Test
    fun `telemetry reported while a write runs is written by the next one`() {
        onWrite = {
            onWrite = {}
            scheduler.write(WriteStrategy.DEBOUNCED, "b")
        }
        scheduler.write(WriteStrategy.DEBOUNCED, "a")
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a")), writes)

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a"), listOf("b")), writes)
    }

    @Test
    fun `a write the guard drops leaves the telemetry for the next one`() {
        writesAllowed = false
        scheduler.write(WriteStrategy.DEBOUNCED, "a")
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(emptyList<List<String>>(), writes)

        writesAllowed = true
        scheduler.write(WriteStrategy.DEBOUNCED, "b")
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a", "b")), writes)
    }

    @Test
    fun `every write reaches the worker through the guard`() {
        scheduler.write(WriteStrategy.DEBOUNCED, "a")
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(1, guardedTasks)

        scheduler.write(WriteStrategy.IMMEDIATE, emptyList())
        assertEquals(2, guardedTasks)
    }

    @Test
    fun `a worker that refuses to schedule a write can still arm the next one`() {
        val rejecting = RejectingScheduleExecutor(executor)
        val rejected = createScheduler(BackgroundWorker(rejecting))

        rejected.write(WriteStrategy.DEBOUNCED, "a")
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(emptyList<List<String>>(), writes)

        rejected.write(WriteStrategy.DEBOUNCED, "b")
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a", "b")), writes)
    }

    @Test
    fun `a write armed as another one drains writes nothing when it runs`() {
        lateinit var racing: TelemetryWriteScheduler<String>
        val hooked = ScheduleHookExecutor(executor) { racing.write(WriteStrategy.IMMEDIATE, emptyList()) }
        racing = createScheduler(BackgroundWorker(hooked))

        racing.write(WriteStrategy.DEBOUNCED, "a")
        assertEquals(listOf(listOf("a")), writes)

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(listOf(listOf("a")), writes)
    }

    private fun createScheduler(worker: BackgroundWorker = BackgroundWorker(executor)) =
        TelemetryWriteScheduler(
            worker = worker,
            delayMs = DELAY_MS,
            queue = TelemetryQueue { it },
            guard = { task ->
                Runnable {
                    guardedTasks++
                    if (writesAllowed) {
                        task.run()
                    }
                }
            },
            onWrite = { records ->
                writes.add(records)
                onWrite(records)
            },
        )

    /**
     * Delegates to [delegate], refusing the first write it is asked to schedule.
     */
    private class RejectingScheduleExecutor(
        private val delegate: ScheduledExecutorService,
    ) : ScheduledExecutorService by delegate {

        private var rejected = false

        override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
            if (!rejected) {
                rejected = true
                throw RejectedExecutionException("worker is not accepting writes")
            }
            return delegate.schedule(command, delay, unit)
        }

        override fun close() {
            super.close()
        }
    }

    /**
     * Delegates to [delegate], calling [onFirstSchedule] once the first write has been scheduled
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

        override fun close() {
            delegate.close()
        }
    }
}
