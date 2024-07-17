package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityCallable
import io.embrace.android.embracesdk.internal.worker.PriorityRunnable
import io.embrace.android.embracesdk.internal.worker.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor

internal class BackgroundWorkerTest {

    @Test
    fun testSubmitRunnable() {
        val impl = BlockableExecutorService()
        var ran = false
        val runnable = Runnable {
            ran = true
        }
        BackgroundWorker(impl).submit(TaskPriority.NORMAL, runnable)
        impl.runNext()
        assertTrue(ran)
    }

    @Test
    fun testSubmitCallable() {
        val impl = BlockableExecutorService()
        var ran = false
        val callable = Callable {
            ran = true
        }
        BackgroundWorker(impl).submit(TaskPriority.NORMAL, callable)
        impl.runNext()
        assertTrue(ran)
    }

    @Test
    fun `test runnable transformed`() {
        val impl = DecoratedExecutorService()
        val runnable = Runnable {}
        val future = BackgroundWorker(impl).submit(TaskPriority.LOW, runnable)
        val submitted = impl.runnables.single() as PriorityRunnable
        assertEquals(TaskPriority.LOW, submitted.priority)
        assertNull(future.get())
    }

    @Test
    fun `test callable transformed`() {
        val impl = DecoratedExecutorService()
        val callable = Callable { "test" }
        val future = BackgroundWorker(impl).submit(TaskPriority.HIGH, callable)
        val submitted = impl.callables.single() as PriorityCallable<*>
        assertEquals(TaskPriority.HIGH, submitted.priority)
        assertEquals("test", future.get())
    }
}

private class DecoratedExecutorService : ScheduledThreadPoolExecutor(1) {
    val runnables = mutableListOf<Runnable>()
    val callables = mutableListOf<Callable<*>>()

    override fun <T : Any?> submit(task: Callable<T>): Future<T> {
        callables.add(task)
        return super.submit(task)
    }

    override fun submit(task: Runnable): Future<*> {
        runnables.add(task)
        return super.submit(task)
    }
}
