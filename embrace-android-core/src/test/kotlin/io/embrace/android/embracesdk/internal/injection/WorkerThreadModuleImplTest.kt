package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class WorkerThreadModuleImplTest {

    @Test
    fun testModule() {
        val module = WorkerThreadModuleImpl()
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
        val module = WorkerThreadModuleImpl()
        val worker = module.backgroundWorker(Worker.Background.PeriodicCacheWorker)
        module.close()

        val future = worker.submit {}
        assertNotNull(future)
    }

    @Test
    fun `await termination waits for running and queued tasks`() {
        val module = WorkerThreadModuleImpl()
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
    fun `close drops delayed tasks`() {
        val module = WorkerThreadModuleImpl()
        val worker = module.backgroundWorker(Worker.Background.IoRegWorker)
        worker.schedule<Unit>({}, 1, TimeUnit.HOURS)
        module.close()
        assertTrue(module.awaitTermination(1000))
    }

    @Test
    fun `worker first requested after close is shut down`() {
        val module = WorkerThreadModuleImpl()
        module.backgroundWorker(Worker.Background.IoRegWorker)
        module.close()

        val ran = AtomicBoolean(false)
        module.backgroundWorker(Worker.Background.NonIoRegWorker).submit { ran.set(true) }
        assertTrue(module.awaitTermination(1000))
        assertFalse(ran.get())
    }

    @Test
    fun `watchdog thread exits when idle and is recreated for new work`() {
        val module = WorkerThreadModuleImpl(watchdogIdleTimeoutMs = IDLE_TIMEOUT_MS)
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
        val module = WorkerThreadModuleImpl(watchdogIdleTimeoutMs = IDLE_TIMEOUT_MS)
        val worker = module.backgroundWorker(Worker.Background.ThreadBlockageWatchdogWorker)

        val thread = worker.submit<Thread> { Thread.currentThread() }.get(1, TimeUnit.SECONDS)
        val future = worker.scheduleAtFixedRate({}, 0, IDLE_TIMEOUT_MS / 2, TimeUnit.MILLISECONDS)
        thread.join(JOIN_TIMEOUT_MS)
        assertTrue(thread.isAlive)

        future.cancel(false)
        thread.join(JOIN_TIMEOUT_MS)
        assertFalse(thread.isAlive)
        module.close()
    }

    @Test
    fun `other worker threads do not exit when idle`() {
        val module = WorkerThreadModuleImpl(watchdogIdleTimeoutMs = IDLE_TIMEOUT_MS)
        val worker = module.backgroundWorker(Worker.Background.NonIoRegWorker)

        val thread = worker.submit<Thread> { Thread.currentThread() }.get(1, TimeUnit.SECONDS)
        thread.join(JOIN_TIMEOUT_MS)
        assertTrue(thread.isAlive)
        module.close()
    }

    private companion object {
        const val IDLE_TIMEOUT_MS = 50L
        const val JOIN_TIMEOUT_MS = 500L
    }
}
