package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityRunnableFuture
import io.embrace.android.embracesdk.internal.worker.PriorityThreadPoolExecutor
import io.embrace.android.embracesdk.internal.worker.PriorityThreadPoolExecutor.Companion.createPriorityQueue
import io.embrace.android.embracesdk.internal.worker.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RejectedExecutionHandler

internal class PriorityThreadPoolExecutorTest {

    private val clock = FakeClock()

    private val executor = PriorityThreadPoolExecutor(
        clock,
        Executors.defaultThreadFactory(),
        RejectedExecutionHandler { _, _ -> },
        1,
        1
    )

    @Before
    fun setUp() {
        clock.setCurrentTime(0)
    }

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
        val future = BackgroundWorker(executor).submit {}
        assertEquals(TaskPriority.NORMAL, (future as PriorityRunnableFuture<*>).priority)
    }

    @Test
    fun `submit valid callable`() {
        val future = BackgroundWorker(executor).submit(TaskPriority.HIGH, Callable {})
        assertEquals(TaskPriority.HIGH, (future as PriorityRunnableFuture<*>).priority)
    }

    @Test
    fun `tasks are processed in priority order when priority is different`() {
        val inputs = listOf(
            createFuture(1, TaskPriority.LOW, 0),
            createFuture(2, TaskPriority.NORMAL, 0),
            createFuture(3, TaskPriority.CRITICAL, 0),
            createFuture(4, TaskPriority.HIGH, 0)
        )
        val queue = createPriorityQueue()
        inputs.forEach(queue::add)
        queue.forEach(Runnable::run)

        val observed = queue.getResults()
        assertEquals(listOf(3, 4, 2, 1), observed)
    }

    @Test
    fun `tasks are in random order when priority + submission time is same`() {
        val inputs = listOf(
            createFuture("elephant", TaskPriority.NORMAL, 0),
            createFuture("apple", TaskPriority.NORMAL, 0),
            createFuture("cat", TaskPriority.NORMAL, 0),
            createFuture("banana", TaskPriority.NORMAL, 0),
            createFuture("zebra", TaskPriority.NORMAL, 0),
            createFuture("dog", TaskPriority.NORMAL, 0),
            createFuture("snake", TaskPriority.NORMAL, 0)
        )
        val queue = createPriorityQueue()
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

    @Test
    fun `tasks are processed in submission order when priority is same`() {
        val inputs = listOf(
            createFuture("elephant", TaskPriority.NORMAL, 0),
            createFuture("apple", TaskPriority.NORMAL, 1),
            createFuture("cat", TaskPriority.NORMAL, 2),
            createFuture("banana", TaskPriority.NORMAL, 3),
            createFuture("zebra", TaskPriority.NORMAL, 4),
            createFuture("dog", TaskPriority.NORMAL, 5),
            createFuture("snake", TaskPriority.NORMAL, 6)
        )
        val queue = createPriorityQueue()
        inputs.forEach(queue::add)
        queue.forEach(Runnable::run)

        val observed = queue.getResults()
        assertEquals(
            listOf(
                "elephant",
                "apple",
                "cat",
                "banana",
                "zebra",
                "dog",
                "snake"
            ),
            observed
        )
    }

    @Test
    fun `tasks are processed with both submission + priority order`() {
        val inputs = listOf(
            createFuture("elephant", TaskPriority.NORMAL, 0),
            createFuture("apple", TaskPriority.HIGH, 1),
            createFuture("cat", TaskPriority.NORMAL, 2),
            createFuture("banana", TaskPriority.LOW, 3),
            createFuture("zebra", TaskPriority.CRITICAL, 4),
            createFuture("dog", TaskPriority.NORMAL, 5),
            createFuture("snake", TaskPriority.NORMAL, 6)
        )
        val queue = createPriorityQueue()
        inputs.forEach(queue::add)
        queue.forEach(Runnable::run)

        val observed = queue.getResults()
        assertEquals(
            listOf(
                "zebra",
                "apple",
                "elephant",
                "cat",
                "dog",
                "snake",
                "banana",
            ),
            observed
        )
    }

    @Test
    fun `resource starvation is mitigated`() {
        val inputs = listOf(
            createFuture("a", TaskPriority.CRITICAL, 0),
            createFuture("c", TaskPriority.HIGH, 1),
            createFuture("b", TaskPriority.CRITICAL, 2),
            createFuture("d", TaskPriority.HIGH, 3).also {
                clock.tick(TaskPriority.HIGH.delayThresholdMs + 1000)
            },
            createFuture("e", TaskPriority.CRITICAL, clock.now()),
        )
        val queue = createPriorityQueue()
        inputs.forEach(queue::add)
        queue.forEach(Runnable::run)

        val observed = queue.getResults()
        assertEquals(
            listOf(
                "a",
                "b",
                "c",
                "d",
                "e",
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
        submitTime: Long
    ) = PriorityRunnableFuture(FutureTask { value }, taskPriority, submitTime)

    /**
     * Drains the results from the queue & returns their Callable value.
     */
    private fun PriorityBlockingQueue<Runnable>.getResults(): List<Any?> {
        val output = mutableListOf<Runnable>()
        drainTo(output)
        return output.map { (it as PriorityRunnableFuture<*>).impl.get() }
    }
}
