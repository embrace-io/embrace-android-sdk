package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class DebouncedTriggerTest {

    private companion object {
        private const val DELAY_MS = 5000L
        private const val QUIESCE_TIMEOUT_MS = 10_000L
    }

    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var trigger: DebouncedTrigger

    private var runs = 0

    @Before
    fun setUp() {
        executor = BlockingScheduledExecutorService(FakeClock(), blockingMode = false)
        runs = 0
        trigger = createTrigger(BackgroundWorker(executor))
    }

    private fun createTrigger(worker: BackgroundWorker) = DebouncedTrigger(worker, DELAY_MS) { runs++ }

    @Test
    fun `a burst of requests costs one run, once the delay has elapsed`() {
        repeat(5) { assertTrue(trigger.arm()) }
        assertEquals(1, executor.scheduledTasksCount())
        assertEquals(0, runs)

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(1, runs)
    }

    @Test
    fun `a request joining an armed run does not postpone it, and a later one arms its own`() {
        trigger.arm()
        executor.moveForwardAndRunBlocked(DELAY_MS / 2)
        trigger.arm()
        assertEquals(0, runs)

        // the run is due a delay after the request that armed it, not after the one that joined it
        executor.moveForwardAndRunBlocked(DELAY_MS / 2)
        assertEquals(1, runs)

        trigger.arm()
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(2, runs)
    }

    @Test
    fun `flush runs the action once, leaving the trigger armable`() {
        trigger.arm()
        trigger.flush()
        assertEquals(1, runs)

        // the disarmed trigger does not run the action again when its delay elapses
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(1, runs)
        assertEquals(0, executor.scheduledTasksCount())

        trigger.arm()
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(2, runs)
    }

    @Test
    fun `flush does not wait for the action to run`() {
        val blockedExecutor = BlockingScheduledExecutorService(FakeClock(), blockingMode = true)
        val blocked = createTrigger(BackgroundWorker(blockedExecutor))

        blocked.arm()
        blocked.flush()
        assertEquals(0, runs)

        blockedExecutor.runCurrentlyBlocked()
        assertEquals(1, runs)
    }

    @Test
    fun `disarm cancels the armed run, and the next request arms a run of its own`() {
        trigger.arm()
        trigger.disarm()

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(0, runs)
        assertEquals(0, executor.scheduledTasksCount())

        assertTrue(trigger.arm())
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(1, runs)
    }

    @Test
    fun `an action that throws does not break the trigger`() {
        val throwing = DebouncedTrigger(BackgroundWorker(executor), DELAY_MS) { error("run failed") }

        throwing.arm()
        runCatching { executor.moveForwardAndRunBlocked(DELAY_MS) }

        assertTrue(throwing.arm())
    }

    @Test
    fun `a trigger that could not be armed arms the next request`() {
        val failing = createTrigger(BackgroundWorker(FailingScheduleExecutor(executor)))

        assertFalse(failing.arm())
        assertTrue(failing.arm())

        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(1, runs)
    }

    @Test
    fun `no change is left without a run to observe it, under contention`() {
        val realExecutor = Executors.newSingleThreadScheduledExecutor()
        val changes = AtomicInteger()
        val observed = AtomicInteger()
        val contended = DebouncedTrigger(BackgroundWorker(realExecutor), delayMs = 1) {
            observed.set(changes.get())
        }

        val threads = List(4) {
            Thread {
                repeat(500) {
                    changes.incrementAndGet()
                    contended.arm()
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        // a lost wakeup would leave the trigger armed with nothing on its way, so the last change
        // would never be observed however long this waited
        val deadline = System.currentTimeMillis() + QUIESCE_TIMEOUT_MS
        while (observed.get() != changes.get() && System.currentTimeMillis() < deadline) {
            Thread.yield()
        }
        assertEquals(changes.get(), observed.get())

        realExecutor.shutdownNow()
    }

    @Test
    fun `a flush racing the arming of a run takes the work once`() {
        var triggerRef: DebouncedTrigger? = null
        val hooked = ScheduleHookExecutor(executor) { checkNotNull(triggerRef).flush() }
        val reentrant = createTrigger(BackgroundWorker(hooked))
        triggerRef = reentrant

        reentrant.arm()
        assertEquals(1, runs)

        // the flush took the work, so the trigger it disarmed must not run again
        executor.moveForwardAndRunBlocked(DELAY_MS)
        assertEquals(1, runs)
    }
}
