package io.embrace.android.embracesdk.concurrency

import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class BlockingScheduledExecutorServiceTest {

    /**
     * A real scheduled executor runs delayed one-shot tasks after shutdown but drops periodic ones. A periodic task that ran
     * on shutdown here would reschedule itself and be drained again, forever.
     */
    @Test
    fun `shutdown runs delayed one-shot tasks and drops periodic ones`() {
        val executor = BlockingScheduledExecutorService(fakeClock = FakeClock(), blockingMode = false)
        var delayedRuns = 0
        var periodicRuns = 0
        executor.schedule({ delayedRuns++ }, 1, TimeUnit.SECONDS)
        val periodic = executor.scheduleWithFixedDelay({ periodicRuns++ }, 1, 1, TimeUnit.SECONDS)

        executor.shutdown()

        assertEquals(1, delayedRuns)
        assertEquals(0, periodicRuns)
        assertTrue(periodic.isCancelled)
        assertEquals(0, executor.scheduledTasksCount())
    }

    @Test
    fun `a periodic task running when the executor shuts down is not rescheduled`() {
        val executor = BlockingScheduledExecutorService(fakeClock = FakeClock(), blockingMode = false)
        var runs = 0
        executor.scheduleAtFixedRate(
            {
                runs++
                executor.shutdown()
            },
            0,
            1,
            TimeUnit.SECONDS,
        )

        assertEquals(1, runs)
        assertEquals(0, executor.scheduledTasksCount())
    }
}
