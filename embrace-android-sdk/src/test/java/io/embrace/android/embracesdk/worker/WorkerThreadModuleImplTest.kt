package io.embrace.android.embracesdk.worker

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class WorkerThreadModuleImplTest {

    @Test
    fun testModule() {
        val module = WorkerThreadModuleImpl()
        assertNotNull(module)

        val backgroundExecutor = module.backgroundExecutor(ExecutorName.SESSION_CACHE_EXECUTOR)
        assertNotNull(backgroundExecutor)
        assertNotNull(module.scheduledExecutor(ExecutorName.SESSION_CACHE_EXECUTOR))

        // test caching
        assertSame(backgroundExecutor, module.backgroundExecutor(ExecutorName.SESSION_CACHE_EXECUTOR))

        // test shutting down module
        module.close()
    }
}
