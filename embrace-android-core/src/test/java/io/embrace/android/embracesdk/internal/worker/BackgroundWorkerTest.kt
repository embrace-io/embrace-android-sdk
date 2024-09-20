package io.embrace.android.embracesdk.internal.worker

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class BackgroundWorkerTest {

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
    fun `test runnable transformed`() {
        val impl = DecoratedExecutorService()
        val runnable = Runnable {}
        val future = PrioritizedWorker(impl).submit(TaskPriority.LOW, runnable)
        val submitted = impl.runnables.single() as PriorityRunnable
        assertEquals(TaskPriority.LOW, submitted.priority)
        assertNull(future.get())
    }

    @Test
    fun `test callable transformed`() {
        val impl = DecoratedExecutorService()
        val callable = Callable { "test" }
        val future = PrioritizedWorker(impl).submit(TaskPriority.HIGH, callable)
        val submitted = impl.callables.single() as PriorityCallable<*>
        assertEquals(TaskPriority.HIGH, submitted.priority)
        assertEquals("test", future.get())
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
        val latch = CountDownLatch(1)
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
        assertFalse(ran)
    }

    private class ShutdownAndWaitExecutorService(
        private val postShutdownAction: () -> Unit = {},
        private val postAwaitTerminationAction: () -> Unit = {},
        private val impl: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
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
