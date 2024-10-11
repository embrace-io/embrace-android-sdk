package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.worker.TaskPriority
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class WorkerThreadModuleImplTest {

    private lateinit var logger: FakeEmbLogger
    private lateinit var initModule: InitModule

    @Before
    fun setup() {
        logger = FakeEmbLogger(false)
        initModule = InitModuleImpl(logger = logger)
    }

    @Test
    fun testModule() {
        val module = WorkerThreadModuleImpl(initModule)
        assertNotNull(module)

        val backgroundExecutor = module.backgroundWorker(Worker.Background.PeriodicCacheWorker)
        assertNotNull(backgroundExecutor)

        // test caching
        assertSame(backgroundExecutor, module.backgroundWorker(Worker.Background.PeriodicCacheWorker))

        // test shutting down module
        module.close()
    }

    @Test
    fun `network request executor uses custom queue`() {
        val module = WorkerThreadModuleImpl(initModule)
        assertNotNull(module.priorityWorker<ApiRequest>(Worker.Priority.NetworkRequestWorker))
        assertNotNull(module.priorityWorker<TaskPriority>(Worker.Priority.DataPersistenceWorker))
    }

    @Test
    fun `rejected execution policy`() {
        val module = WorkerThreadModuleImpl(initModule)
        val worker = module.backgroundWorker(Worker.Background.PeriodicCacheWorker)
        module.close()

        val future = worker.submit {}
        assertNotNull(future)
    }
}
