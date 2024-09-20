package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class WorkerThreadModuleImplTest {

    private lateinit var fakeInternalErrorService: FakeInternalErrorService
    private lateinit var logger: EmbLoggerImpl
    private lateinit var initModule: InitModule

    @Before
    fun setup() {
        fakeInternalErrorService = FakeInternalErrorService()
        logger = EmbLoggerImpl().apply { internalErrorService = fakeInternalErrorService }
        initModule = InitModuleImpl(logger = logger)
    }

    @Test
    fun testModule() {
        val module = WorkerThreadModuleImpl(initModule)
        assertNotNull(module)

        val backgroundExecutor = module.backgroundWorker(Worker.PeriodicCacheWorker)
        assertNotNull(backgroundExecutor)

        // test caching
        assertSame(backgroundExecutor, module.backgroundWorker(Worker.PeriodicCacheWorker))

        // test shutting down module
        module.close()
    }

    @Test
    fun `network request executor uses custom queue`() {
        val module = WorkerThreadModuleImpl(initModule)
        assertNotNull(module.prioritizedWorker(Worker.NetworkRequestWorker))
    }

    @Test
    fun `rejected execution policy`() {
        val module = WorkerThreadModuleImpl(initModule)
        val worker = module.backgroundWorker(Worker.PeriodicCacheWorker)
        module.close()

        val future = worker.submit {}
        assertNotNull(future)
    }
}
