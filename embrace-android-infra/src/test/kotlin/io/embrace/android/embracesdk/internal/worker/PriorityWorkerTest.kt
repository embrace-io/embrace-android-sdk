package io.embrace.android.embracesdk.internal.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class PriorityWorkerTest {

    private val noopComparator = Comparator<Runnable> { _, _ -> 0 }

    @Test
    fun `cancelAndRemove evicts a still-queued task from a real ThreadPoolExecutor`() {
        val executor = PriorityThreadPoolExecutor(
            Executors.defaultThreadFactory(),
            { _, _ -> },
            1,
            1,
            noopComparator,
        )
        val worker = PriorityWorker<String>(executor)
        val latch = CountDownLatch(1)

        worker.submit("blocker", Runnable { latch.await(1000, TimeUnit.MILLISECONDS) })
        val queued = worker.submit("queued", Runnable { })

        assertTrue(worker.cancelAndRemove(queued))
        assertFalse(executor.queue.contains(queued as Runnable))

        latch.countDown()
        worker.shutdownAndWait(1000)
    }

    @Test
    fun `cancelAndRemove is a safe no-op when the executor is not a ThreadPoolExecutor`() {
        val executor = Executors.newSingleThreadExecutor()
        val worker = PriorityWorker<String>(executor)
        val latch = CountDownLatch(1)

        worker.submit("blocker", Runnable { latch.await(1000, TimeUnit.MILLISECONDS) })
        val queued = worker.submit("queued", Runnable { })

        assertTrue(worker.cancelAndRemove(queued))

        latch.countDown()
        worker.shutdownAndWait(1000)
    }

    @Test
    fun `cancelAndRemove returns false and removes nothing once the task has already run`() {
        val executor = PriorityThreadPoolExecutor(
            Executors.defaultThreadFactory(),
            { _, _ -> },
            1,
            1,
            noopComparator,
        )
        val worker = PriorityWorker<String>(executor)

        val future = worker.submit("task", Runnable { })
        worker.shutdownAndWait(1000)

        assertFalse(worker.cancelAndRemove(future))
    }
}
