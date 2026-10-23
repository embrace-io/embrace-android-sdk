package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class WorkerThreadPoolImplTest {

    @Test
    fun testModule() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler())
        assertNotNull(module)

        val backgroundExecutor = module.backgroundWorker(Worker.Background.PeriodicCacheWorker)
        assertNotNull(backgroundExecutor)

        // test caching
        assertSame(backgroundExecutor, module.backgroundWorker(Worker.Background.PeriodicCacheWorker))

        // test shutting down module
        module.close()
    }

    @Test
    fun `rejected execution policy`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler())
        val worker = module.backgroundWorker(Worker.Background.PeriodicCacheWorker)
        module.close()

        val future = worker.submit {}
        assertTrue(future.isCancelled)
    }

    @Test
    fun `rejected priority task is cancelled`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler())
        val worker = module.priorityWorker<String>(Worker.Priority.DataPersistenceWorker)
        module.close()

        val future = worker.submit("p", Callable { 1 })
        assertTrue(future.isCancelled)
    }

    @Test
    fun `await termination waits for running and queued tasks`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler())
        val worker = module.backgroundWorker(Worker.Background.IoRegWorker)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = mutableListOf<Int>()
        worker.submit {
            started.countDown()
            release.await()
            completed += 1
        }
        worker.submit { completed += 2 }
        started.await(1, TimeUnit.SECONDS)
        module.close()

        assertFalse(module.awaitTermination(50))
        release.countDown()
        assertTrue(module.awaitTermination(1000))
        assertEquals(listOf(1, 2), completed)
    }

    @Test
    fun `close drops delayed and periodic tasks but runs queued tasks`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler())
        val worker = module.backgroundWorker(Worker.Background.IoRegWorker)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        worker.submit {
            started.countDown()
            release.await()
        }
        val delayed = worker.schedule<Unit>({}, 1, TimeUnit.HOURS)
        val periodic = worker.scheduleAtFixedRate({}, 0, 1, TimeUnit.HOURS)
        val queued = worker.submit {}
        started.await(1, TimeUnit.SECONDS)
        module.close()
        release.countDown()

        assertTrue(module.awaitTermination(1000))
        assertTrue(delayed.isCancelled)
        assertTrue(periodic.isCancelled)
        assertTrue(queued.isDone && !queued.isCancelled)
    }

    @Test
    fun `worker first requested after close is shut down`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler())
        module.backgroundWorker(Worker.Background.IoRegWorker)
        module.close()

        val ran = AtomicBoolean(false)
        module.backgroundWorker(Worker.Background.NonIoRegWorker).submit { ran.set(true) }
        assertTrue(module.awaitTermination(1000))
        assertFalse(ran.get())
    }

    @Test
    fun `watchdog thread exits when idle and is recreated for new work`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler(), watchdogIdleTimeoutMs = IDLE_TIMEOUT_MS)
        val worker = module.backgroundWorker(Worker.Background.ThreadBlockageWatchdogWorker)

        val first = worker.submit<Thread> { Thread.currentThread() }.get(1, TimeUnit.SECONDS)
        first.join(JOIN_TIMEOUT_MS)
        assertFalse(first.isAlive)

        val second = worker.submit<Thread> { Thread.currentThread() }.get(1, TimeUnit.SECONDS)
        assertNotSame(first, second)
        assertSame(second, module.threadBlockageMonitorThread.get())
        module.close()
    }

    @Test
    fun `watchdog thread stays alive while a task is scheduled`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler(), watchdogIdleTimeoutMs = IDLE_TIMEOUT_MS)
        val worker = module.backgroundWorker(Worker.Background.ThreadBlockageWatchdogWorker)

        val thread = worker.submit<Thread> { Thread.currentThread() }.get(1, TimeUnit.SECONDS)
        val future = worker.scheduleAtFixedRate({}, 0, IDLE_TIMEOUT_MS / 2, TimeUnit.MILLISECONDS)
        thread.join(ALIVE_CHECK_MS)
        assertTrue(thread.isAlive)

        future.cancel(false)
        thread.join(JOIN_TIMEOUT_MS)
        assertFalse(thread.isAlive)
        module.close()
    }

    @Test
    fun `other worker threads do not exit when idle`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler(), watchdogIdleTimeoutMs = IDLE_TIMEOUT_MS)
        val worker = module.backgroundWorker(Worker.Background.NonIoRegWorker)

        val thread = worker.submit<Thread> { Thread.currentThread() }.get(1, TimeUnit.SECONDS)
        thread.join(ALIVE_CHECK_MS)
        assertTrue(thread.isAlive)
        module.close()
    }

    @Test
    fun `failed tasks are tracked as sdk errors`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val module = WorkerThreadPoolImpl(errorHandler)
        val background = module.backgroundWorker(Worker.Background.IoRegWorker)
        background.submit { error("submit") }
        background.scheduleAtFixedRate({ error("periodic") }, 0, 1, TimeUnit.HOURS)
        background.schedule<Unit>({}, 1, TimeUnit.HOURS).cancel(false)
        background.submit {}.get(1, TimeUnit.SECONDS)
        module.close()

        assertTrue(module.awaitTermination(1000))
        assertEquals(
            listOf("submit", "periodic"),
            errorHandler.sdkErrorMessages.map { it.throwable?.message },
        )
        assertTrue(errorHandler.sdkErrorMessages.all { it.msg == "WorkerTaskFail" })
    }

    @Test
    fun `error handler failure does not kill the worker thread`() {
        val module = WorkerThreadPoolImpl(FakeErrorHandler(throwOnSdkError = true))
        val worker = module.backgroundWorker(Worker.Background.IoRegWorker)
        val first = worker.submit<Thread> { Thread.currentThread() }.get(1, TimeUnit.SECONDS)
        worker.submit { error("fail") }
        val second = worker.submit<Thread> { Thread.currentThread() }.get(1, TimeUnit.SECONDS)
        assertSame(first, second)
        module.close()
    }

    @Test
    fun `failed priority tasks are tracked as sdk errors`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val module = WorkerThreadPoolImpl(errorHandler)
        module.priorityWorker<String>(Worker.Priority.DataPersistenceWorker).submit("p") { error("priority") }
        module.close()

        assertTrue(module.awaitTermination(1000))
        assertEquals("priority", errorHandler.sdkErrorMessages.single().throwable?.message)
    }

    private companion object {
        const val IDLE_TIMEOUT_MS = 50L
        const val JOIN_TIMEOUT_MS = 500L
        const val ALIVE_CHECK_MS = IDLE_TIMEOUT_MS * 4
    }
}
