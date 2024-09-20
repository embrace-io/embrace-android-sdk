package io.embrace.android.embracesdk.internal.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Callable

internal class PriorityWorkerTest {

    @Test
    fun `test runnable transformed`() {
        val impl = DecoratedExecutorService()
        val runnable = Runnable {}
        val future = PriorityWorker<TaskPriority>(impl).submit(TaskPriority.LOW, runnable)
        val submitted = impl.runnables.single() as PriorityRunnable
        assertEquals(TaskPriority.LOW, submitted.priorityInfo)
        assertNull(future.get())
    }

    @Test
    fun `test callable transformed`() {
        val impl = DecoratedExecutorService()
        val callable = Callable { "test" }
        val future = PriorityWorker<TaskPriority>(impl).submit(TaskPriority.HIGH, callable)
        val submitted = impl.callables.single() as PriorityCallable<*>
        assertEquals(TaskPriority.HIGH, submitted.priorityInfo)
        assertEquals("test", future.get())
    }
}
