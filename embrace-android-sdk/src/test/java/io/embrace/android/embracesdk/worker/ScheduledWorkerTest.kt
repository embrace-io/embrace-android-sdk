package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class ScheduledWorkerTest {

    @Test
    fun testSchedule() {
        val impl = BlockingScheduledExecutorService()
        var ran = false
        val runnable = Runnable {
            ran = true
        }
        ScheduledWorker(impl).schedule<Unit>(runnable, 5, TimeUnit.SECONDS)
        assertFalse(ran)
        impl.moveForwardAndRunBlocked(5000)
        assertTrue(ran)
    }

    @Test
    fun testScheduleWithFixedDelay() {
        val impl = BlockingScheduledExecutorService()
        var count = 0
        val runnable = Runnable {
            count++
        }
        ScheduledWorker(impl).scheduleWithFixedDelay(runnable, 2, 2, TimeUnit.SECONDS)

        repeat(3) {
            impl.moveForwardAndRunBlocked(2000)
            assertEquals(it + 1, count)
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun testScheduleAtFixedRate() {
        val impl = BlockingScheduledExecutorService()
        var count = 0
        val runnable = Runnable {
            count++
        }
        ScheduledWorker(impl).scheduleAtFixedRate(runnable, 2, 2, TimeUnit.SECONDS)

        repeat(3) {
            impl.moveForwardAndRunBlocked(2000)
            assertEquals(it + 1, count)
        }
    }

    @Test
    fun testSubmitRunnable() {
        val impl = BlockingScheduledExecutorService()
        var ran = false
        val runnable = Runnable {
            ran = true
        }
        ScheduledWorker(impl).submit(runnable)
        impl.runAllSubmittedTasks()
        assertTrue(ran)
    }
}
