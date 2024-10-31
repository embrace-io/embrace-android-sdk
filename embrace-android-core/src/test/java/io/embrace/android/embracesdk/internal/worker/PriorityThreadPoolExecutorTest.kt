package io.embrace.android.embracesdk.internal.worker

import io.embrace.android.embracesdk.internal.worker.comparator.taskPriorityComparator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue

internal class PriorityThreadPoolExecutorTest {

    private val executor = PriorityThreadPoolExecutor(
        Executors.defaultThreadFactory(),
        { _, _ -> },
        1,
        1,
        taskPriorityComparator
    )

    @Test(expected = IllegalArgumentException::class)
    fun `reject invalid runnable`() {
        executor.submit(Runnable {})
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reject invalid callable`() {
        executor.submit(Callable {})
    }

    @Test
    fun `submit valid runnable`() {
        val future = PriorityWorker<TaskPriority>(executor).submit(TaskPriority.NORMAL) {}
        assertEquals(TaskPriority.NORMAL, (future as PriorityRunnableFuture<*>).priorityInfo)
    }

    @Test
    fun `submit valid callable`() {
        val future = PriorityWorker<TaskPriority>(executor).submit(TaskPriority.HIGH, Callable {})
        assertEquals(TaskPriority.HIGH, (future as PriorityRunnableFuture<*>).priorityInfo)
    }

    @Test
    fun `tasks are processed in priority order when priority is different`() {
        val inputs = listOf(
            createFuture(1, TaskPriority.LOW),
            createFuture(2, TaskPriority.NORMAL),
            createFuture(3, TaskPriority.CRITICAL),
            createFuture(4, TaskPriority.HIGH)
        )
        val queue = PriorityBlockingQueue<Runnable>(
            100,
            taskPriorityComparator
        )
        inputs.forEach(queue::add)
        queue.forEach(Runnable::run)

        val observed = queue.getResults()
        assertEquals(listOf(3, 4, 2, 1), observed)
    }

    @Test
    fun `tasks are in random order when priority + submission time is same`() {
        val inputs = listOf(
            createFuture("elephant", TaskPriority.NORMAL),
            createFuture("apple", TaskPriority.NORMAL),
            createFuture("cat", TaskPriority.NORMAL),
            createFuture("banana", TaskPriority.NORMAL),
            createFuture("zebra", TaskPriority.NORMAL),
            createFuture("dog", TaskPriority.NORMAL),
            createFuture("snake", TaskPriority.NORMAL)
        )
        val queue = PriorityBlockingQueue<Runnable>(
            100,
            taskPriorityComparator
        )
        inputs.forEach(queue::add)
        queue.forEach(Runnable::run)

        val observed = queue.getResults()
        assertEquals(
            listOf(
                "elephant",
                "snake",
                "dog",
                "zebra",
                "banana",
                "cat",
                "apple"
            ),
            observed
        )
    }

    /**
     * Syntactic sugar for creating a [PriorityRunnableFuture].
     */
    private fun <T> createFuture(
        value: T,
        taskPriority: TaskPriority,
    ) = PriorityRunnableFuture(FutureTask { value }, taskPriority)

    /**
     * Drains the results from the queue & returns their Callable value.
     */
    private fun PriorityBlockingQueue<Runnable>.getResults(): List<Any?> {
        val output = mutableListOf<Runnable>()
        drainTo(output)
        return output.map { (it as PriorityRunnableFuture<*>).impl.get() }
    }
}
