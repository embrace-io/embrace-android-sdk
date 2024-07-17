package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.WorkerName
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class WorkerThreadModuleImplTest {

    private lateinit var fakeInternalErrorService: FakeInternalErrorService
    private lateinit var logger: EmbLoggerImpl
    private lateinit var initModule: InitModule
    private lateinit var coreModule: CoreModule

    @Before
    fun setup() {
        fakeInternalErrorService = FakeInternalErrorService()
        logger = EmbLoggerImpl().apply { internalErrorService = fakeInternalErrorService }
        initModule = InitModuleImpl(logger = logger)
        coreModule = FakeCoreModule(logger = logger)
    }

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
        val module = WorkerThreadModuleImpl(initModule)
        val worker = module.backgroundWorker(WorkerName.PERIODIC_CACHE)
        module.close()

        val future = worker.submit {}
        assertNotNull(future)
    }
}
