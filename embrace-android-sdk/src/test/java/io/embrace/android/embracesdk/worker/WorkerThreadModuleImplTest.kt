package io.embrace.android.embracesdk.worker

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

internal class WorkerThreadModuleImplTest {

    @Test
    fun testModule() {
        val module = WorkerThreadModuleImpl()
        assertNotNull(module)

        assertNotNull(module.backgroundExecutor(ExecutorName.SESSION))
        val backgroundExecutor = module.backgroundExecutor(ExecutorName.SESSION_CACHE_EXECUTOR)
        assertNotNull(backgroundExecutor)
        assertNotNull(module.scheduledExecutor(ExecutorName.SESSION_CACHE_EXECUTOR))

        // test caching
        assertSame(backgroundExecutor, module.backgroundExecutor(ExecutorName.SESSION_CACHE_EXECUTOR))

        // test shutting down module
        module.close()
        try {
            module.backgroundExecutor(ExecutorName.SESSION).submit {}
            fail("Should have thrown RejectedExecutionException")
        } catch (ignored: RejectedExecutionException) {
        }
    }
}
