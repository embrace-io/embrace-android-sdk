package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

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
