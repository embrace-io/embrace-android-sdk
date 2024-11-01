package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.worker.TaskPriority
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

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
    fun `network request executor uses custom queue`() {
        val module = WorkerThreadModuleImpl()
        assertNotNull(module.priorityWorker<ApiRequest>(Worker.Priority.NetworkRequestWorker))
        assertNotNull(module.priorityWorker<TaskPriority>(Worker.Priority.DataPersistenceWorker))
    }

    @Test
    fun `rejected execution policy`() {
        val module = WorkerThreadModuleImpl()
        val worker = module.backgroundWorker(Worker.Background.PeriodicCacheWorker)
        module.close()

        val future = worker.submit {}
        assertNotNull(future)
    }
}
