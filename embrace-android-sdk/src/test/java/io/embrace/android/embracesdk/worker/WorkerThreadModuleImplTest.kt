package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.fakes.FakeLoggerAction
import io.embrace.android.embracesdk.injection.InitModuleImpl
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class WorkerThreadModuleImplTest {

    private val initModule = InitModuleImpl()

    @Test
    fun testModule() {
        val module = WorkerThreadModuleImpl(initModule)
        assertNotNull(module)

        val backgroundExecutor = module.backgroundWorker(WorkerName.PERIODIC_CACHE)
        assertNotNull(backgroundExecutor)
        val scheduledExecutor = module.scheduledWorker(WorkerName.PERIODIC_CACHE)
        assertNotNull(scheduledExecutor)

        // test caching
        assertSame(backgroundExecutor, module.backgroundWorker(WorkerName.PERIODIC_CACHE))
        assertSame(scheduledExecutor, module.scheduledWorker(WorkerName.PERIODIC_CACHE))

        // test shutting down module
        module.close()
    }

    @Test
    fun `network request executor uses custom queue`() {
        val module = WorkerThreadModuleImpl(initModule)
        assertNotNull(module.backgroundWorker(WorkerName.NETWORK_REQUEST))
    }

    @Test(expected = IllegalStateException::class)
    fun `network request scheduled executor fails`() {
        val module = WorkerThreadModuleImpl(initModule)
        module.scheduledWorker(WorkerName.NETWORK_REQUEST)
    }

    @Test
    fun `rejected execution policy`() {
        val action = FakeLoggerAction()
        InternalStaticEmbraceLogger.logger.addLoggerAction(action)
        val module = WorkerThreadModuleImpl(initModule)
        val worker = module.backgroundWorker(WorkerName.PERIODIC_CACHE)
        module.close()

        val future = worker.submit {}
        val msg = action.msgQueue.single().msg
        assertTrue(msg.startsWith("Rejected execution of"))
        assertNotNull(future)
        InternalStaticEmbraceLogger.logger.setToDefault()
    }
}
