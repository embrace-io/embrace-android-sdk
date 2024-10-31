package io.embrace.android.embracesdk.internal.worker

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class BackgroundWorkerTest {

    @Test
    fun testSchedule() {
        val impl = BlockingScheduledExecutorService()
        var ran = false
        val runnable = Runnable {
            ran = true
        }
        BackgroundWorker(impl)
            .schedule<Unit>(runnable, 5, TimeUnit.SECONDS)
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
        BackgroundWorker(impl)
            .scheduleWithFixedDelay(runnable, 2, 2, TimeUnit.SECONDS)

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
        BackgroundWorker(impl)
            .scheduleAtFixedRate(runnable, 2, 2, TimeUnit.SECONDS)

        repeat(3) {
            impl.moveForwardAndRunBlocked(2000)
            assertEquals(it + 1, count)
        }
    }

    @Test
    fun testSubmitRunnable() {
        val impl = BlockingScheduledExecutorService(blockingMode = false)
        var ran = false
        val runnable = Runnable {
            ran = true
        }
        BackgroundWorker(impl).submit(runnable)
        impl.runCurrentlyBlocked()
        assertTrue(ran)
    }

    @Test
    fun testSubmitCallable() {
        val impl = BlockingScheduledExecutorService(blockingMode = false)
        var ran = false
        val callable = Callable {
            ran = true
        }
        BackgroundWorker(impl).submit(callable)
        impl.runCurrentlyBlocked()
        assertTrue(ran)
    }

    @Test
    fun `shutdown and wait within timeout`() {
        val latch = CountDownLatch(1)
        val worker = BackgroundWorker(
            ShutdownAndWaitExecutorService(postShutdownAction = {
                latch.countDown()
            })
        )

        var ran = false
        worker.submit {
            latch.await(1000, TimeUnit.MILLISECONDS)
            ran = true
        }
        worker.shutdownAndWait(100)
        assertTrue(ran)
    }

    @Test
    fun `shutdown and wait exceeds timeout`() {
        val latch = CountDownLatch(2)
        val worker = BackgroundWorker(
            ShutdownAndWaitExecutorService(postAwaitTerminationAction = {
                latch.countDown()
            })
        )

        var ran = false
        worker.submit {
            latch.await(1000, TimeUnit.MILLISECONDS)
            ran = true
        }
        worker.shutdownAndWait(0)
        assertEquals(1, latch.count)
        assertFalse(ran)
    }

    private class ShutdownAndWaitExecutorService(
        private val postShutdownAction: () -> Unit = {},
        private val postAwaitTerminationAction: () -> Unit = {},
        private val impl: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    ) : ScheduledExecutorService by impl {

        override fun shutdown() {
            impl.shutdown()
            postShutdownAction()
        }

        override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean {
            val result = impl.awaitTermination(timeout, unit)
            postAwaitTerminationAction()
            return result
        }
    }
}
