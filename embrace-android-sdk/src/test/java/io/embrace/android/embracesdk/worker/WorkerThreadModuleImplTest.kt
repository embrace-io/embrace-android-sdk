package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.fakes.FakeLoggerAction
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ThreadPoolExecutor

internal class WorkerThreadModuleImplTest {

    @Test
    fun testModule() {
        val module = WorkerThreadModuleImpl()
        assertNotNull(module)

        val backgroundExecutor = module.backgroundExecutor(ExecutorName.PERIODIC_CACHE)
        assertNotNull(backgroundExecutor)
        val scheduledExecutor = module.scheduledExecutor(ExecutorName.PERIODIC_CACHE)
        assertNotNull(scheduledExecutor)

        // test caching
        assertSame(backgroundExecutor, module.backgroundExecutor(ExecutorName.PERIODIC_CACHE))
        assertSame(backgroundExecutor, scheduledExecutor)

        // test shutting down module
        module.close()
    }

    @Test
    fun `network request executor uses custom queue`() {
        val module = WorkerThreadModuleImpl()
        assertTrue(module.backgroundExecutor(ExecutorName.NETWORK_REQUEST) is ThreadPoolExecutor)
    }

    @Test(expected = IllegalStateException::class)
    fun `network request scheduled executor fails`() {
        val module = WorkerThreadModuleImpl()
        assertTrue(module.scheduledExecutor(ExecutorName.NETWORK_REQUEST) is ThreadPoolExecutor)
    }

    @Test
    fun `rejected execution policy`() {
        val action = FakeLoggerAction()
        val logger = InternalEmbraceLogger().apply { addLoggerAction(action) }
        val module = WorkerThreadModuleImpl(logger)

        val executor = module.backgroundExecutor(ExecutorName.PERIODIC_CACHE)
        executor.shutdown()

        val future = executor.submit {}
        val msg = action.msgQueue.single().msg
        assertTrue(msg.startsWith("Rejected execution of"))
        assertNotNull(future)
    }
}
