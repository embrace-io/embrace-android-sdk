package io.embrace.android.embracesdk.worker

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

        val backgroundExecutor = module.backgroundExecutor(ExecutorName.SESSION_CACHE_EXECUTOR)
        assertNotNull(backgroundExecutor)
        val scheduledExecutor = module.scheduledExecutor(ExecutorName.SESSION_CACHE_EXECUTOR)
        assertNotNull(scheduledExecutor)

        // test caching
        assertSame(backgroundExecutor, module.backgroundExecutor(ExecutorName.SESSION_CACHE_EXECUTOR))
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
}
